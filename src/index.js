// Límites de tamaño: acotan el coste máximo de cada petición.
const MAX_JSON_BYTES = 256 * 1024; // consultas de texto a Gemini
const MAX_AUDIO_BYTES = 4 * 1024 * 1024; // fragmento de audio para reconocer música

// Solo se permiten estos modelos y este método de Gemini.
const RUTA_GEMINI = /^\/v1beta\/models\/gemini-2\.5-flash(-lite)?:generateContent$/;
const RUTA_AUDD = "/recognize";

// Campos que la app puede enviar a Gemini; el resto se descarta.
const CAMPOS_GEMINI = ["systemInstruction", "contents", "tools", "generationConfig"];

function respuesta(estado, cuerpo) {
  return new Response(typeof cuerpo === "string" ? cuerpo : JSON.stringify(cuerpo), {
    status: estado,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

// Mismo formato de error que usa Gemini: {"error":{"message":"..."}}
function error(estado, mensaje) {
  return respuesta(estado, { error: { message: mensaje } });
}

// Comparación en tiempo constante (evita adivinar el token por diferencias de tiempo).
function iguales(a, b) {
  const enc = new TextEncoder();
  const x = enc.encode(a);
  const y = enc.encode(b);
  let diff = x.length ^ y.length;
  const n = Math.max(x.length, y.length);
  for (let i = 0; i < n; i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

// Lee el cuerpo como bytes y corta si supera el máximo (no confía en Content-Length).
async function leerLimitado(request, maximo) {
  const declarado = Number(request.headers.get("Content-Length"));
  if (Number.isFinite(declarado) && declarado > maximo) return null;
  if (!request.body) return new Uint8Array(0);

  const lector = request.body.getReader();
  const partes = [];
  let total = 0;
  while (true) {
    const { done, value } = await lector.read();
    if (done) break;
    total += value.byteLength;
    if (total > maximo) {
      await lector.cancel();
      return null;
    }
    partes.push(value);
  }
  const todo = new Uint8Array(total);
  let pos = 0;
  for (const p of partes) {
    todo.set(p, pos);
    pos += p.byteLength;
  }
  return todo;
}

async function reconocerMusica(request, env) {
  const tipo = request.headers.get("Content-Type") || "";
  if (!tipo.toLowerCase().startsWith("multipart/form-data")) {
    return error(400, "Formato no válido");
  }
  const bytes = await leerLimitado(request, MAX_AUDIO_BYTES);
  if (bytes === null) return error(413, "Audio demasiado grande");

  let formulario;
  try {
    formulario = await new Response(bytes, { headers: { "Content-Type": tipo } }).formData();
  } catch {
    return error(400, "Formato no válido");
  }
  const archivo = formulario.get("file");
  if (!archivo || typeof archivo === "string") return error(400, "Falta el archivo de audio");

  // Se arma un formulario nuevo: solo viaja lo imprescindible hacia AudD.
  const salida = new FormData();
  salida.set("api_token", env.AUDD_API_TOKEN);
  salida.set("return", "spotify,apple_music");
  salida.set("file", archivo, archivo.name || "audio.mp3");

  const audd = await fetch("https://api.audd.io/", { method: "POST", body: salida });
  return respuesta(audd.status, await audd.text());
}

async function consultarGemini(request, env, ruta) {
  const bytes = await leerLimitado(request, MAX_JSON_BYTES);
  if (bytes === null) return error(413, "Consulta demasiado grande");

  let entrada;
  try {
    entrada = JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return error(400, "JSON no válido");
  }
  if (entrada === null || typeof entrada !== "object" || Array.isArray(entrada)) {
    return error(400, "JSON no válido");
  }

  const cuerpo = {};
  for (const campo of CAMPOS_GEMINI) {
    if (campo in entrada) cuerpo[campo] = entrada[campo];
  }
  // Solo se permite la herramienta de búsqueda de Google
  if (Array.isArray(cuerpo.tools)) {
    cuerpo.tools = cuerpo.tools.filter(
      (t) => t && typeof t === "object" && Object.keys(t).every((k) => k === "google_search")
    );
  }
  if (!Array.isArray(cuerpo.contents) || cuerpo.contents.length === 0) {
    return error(400, "Falta el contenido de la consulta");
  }

  // Sin query string: la app no lo necesita y así nadie puede añadir parámetros (p. ej. ?key=)
  const gemini = await fetch(`https://generativelanguage.googleapis.com${ruta}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-goog-api-key": env.GEMINI_API_KEY,
    },
    body: JSON.stringify(cuerpo),
  });
  return respuesta(gemini.status, await gemini.text());
}

export default {
  async fetch(request, env) {
    // Esta API es solo para la app (no para navegadores): no hay cabeceras CORS.
    if (request.method !== "POST") {
      return new Response("Método no permitido", { status: 405, headers: { Allow: "POST" } });
    }

    try {
      const ruta = new URL(request.url).pathname;
      const esAudd = ruta === RUTA_AUDD;
      const esGemini = RUTA_GEMINI.test(ruta);
      if (!esAudd && !esGemini) return error(404, "No encontrado");

      // 1) Token de la app
      if (env.APP_TOKEN) {
        const enviado = request.headers.get("X-App-Token") || "";
        if (!iguales(enviado, env.APP_TOKEN)) {
          if (env.TOKEN_MODE === "enforce") return error(401, "No autorizado");
          console.warn("Petición sin token válido (modo de transición)", ruta);
        }
      }

      // 2) Límite de peticiones (si el binding está configurado)
      if (env.RATE_LIMITER) {
        const clave = request.headers.get("CF-Connecting-IP") || "desconocida";
        const { success } = await env.RATE_LIMITER.limit({ key: clave });
        if (!success) return error(429, "Demasiadas peticiones, espera un momento");
      }

      // 3) Secretos presentes
      if (esAudd && !env.AUDD_API_TOKEN) {
        console.error("Falta el secreto AUDD_API_TOKEN");
        return error(500, "Servicio no disponible");
      }
      if (esGemini && !env.GEMINI_API_KEY) {
        console.error("Falta el secreto GEMINI_API_KEY");
        return error(500, "Servicio no disponible");
      }

      return esAudd ? await reconocerMusica(request, env) : await consultarGemini(request, env, ruta);
    } catch (e) {
      // El detalle va al log del Worker, nunca a quien llama
      console.error("Error interno:", e);
      return error(500, "Error interno");
    }
  },
};
