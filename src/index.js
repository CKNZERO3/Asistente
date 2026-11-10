// Límites de tamaño: acotan el coste máximo de cada petición.
const MAX_JSON_BYTES = 256 * 1024;
const MAX_AUDIO_BYTES = 4 * 1024 * 1024;

// Lista explícita de modelos de texto permitidos para generateContent.
// TTS, Live, embeddings y transcripción tendrán rutas y clientes independientes.
const MODELOS_GEMINI = new Set([
  "gemini-2.5-flash",
  "gemini-2.5-flash-lite",
  "gemini-3-flash-preview",
  "gemini-3.1-flash-lite",
  "gemini-3.5-flash",
  "gemini-3.5-flash-lite",
  "gemini-3.6-flash",
  "gemini-3.8-flash"
]);

const RUTA_GEMINI = /^\/v1beta\/models\/([a-z0-9-]+):generateContent$/;
const RUTA_AUDD = "/recognize";
const CAMPOS_GEMINI = ["systemInstruction", "contents", "tools", "generationConfig"];

function respuesta(estado, cuerpo) {
  return new Response(typeof cuerpo === "string" ? cuerpo : JSON.stringify(cuerpo), {
    status: estado,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store"
    }
  });
}

function error(estado, mensaje) {
  return respuesta(estado, { error: { message: mensaje } });
}

function iguales(a, b) {
  const enc = new TextEncoder();
  const x = enc.encode(a);
  const y = enc.encode(b);
  let diff = x.length ^ y.length;
  const n = Math.max(x.length, y.length);
  for (let i = 0; i < n; i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

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
  for (const parte of partes) {
    todo.set(parte, pos);
    pos += parte.length;
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
  if (!archivo || typeof archivo === "string") {
    return error(400, "Falta el archivo de audio");
  }

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
    if (Object.prototype.hasOwnProperty.call(entrada, campo)) {
      cuerpo[campo] = entrada[campo];
    }
  }

  // Solo se permite Google Search. Las herramientas Android no se reenvían a Google.
  if (Array.isArray(cuerpo.tools)) {
    cuerpo.tools = cuerpo.tools.filter(
      (herramienta) =>
        herramienta &&
        typeof herramienta === "object" &&
        Object.keys(herramienta).every((clave) => clave === "google_search")
    );
    if (cuerpo.tools.length === 0) delete cuerpo.tools;
  }

  if (!Array.isArray(cuerpo.contents) || cuerpo.contents.length === 0) {
    return error(400, "Falta el contenido de la consulta");
  }

  const gemini = await fetch("https://generativelanguage.googleapis.com" + ruta, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-goog-api-key": env.GEMINI_API_KEY
    },
    body: JSON.stringify(cuerpo)
  });

  // Conserva el estado y la respuesta originales de Google para el diagnóstico Android.
  const contenido = await gemini.text();
  return new Response(contenido, {
    status: gemini.status,
    headers: {
      "Content-Type": gemini.headers.get("Content-Type") || "application/json",
      "Cache-Control": "no-store"
    }
  });
}

export default {
  async fetch(request, env) {
    if (request.method !== "POST") {
      return new Response("Método no permitido", {
        status: 405,
        headers: { Allow: "POST" }
      });
    }

    try {
      const ruta = new URL(request.url).pathname;
      const esAudd = ruta === RUTA_AUDD;
      const coincidenciaGemini = ruta.match(RUTA_GEMINI);
      const modeloGemini = coincidenciaGemini?.[1] || null;
      const esGemini = modeloGemini !== null;

      if (!esAudd && !esGemini) return error(404, "Ruta no encontrada");
      if (esGemini && !MODELOS_GEMINI.has(modeloGemini)) {
        return error(404, "Modelo no habilitado en Iris: " + modeloGemini);
      }

      // 1) Token de la aplicación.
      if (env.APP_TOKEN) {
        const enviado = request.headers.get("X-App-Token") || "";
        if (!iguales(enviado, env.APP_TOKEN)) {
          if (env.TOKEN_MODE === "enforce") return error(401, "No autorizado");
          console.warn("Petición sin token válido (modo de transición)", ruta);
        }
      }

      // 2) Límite adicional del Worker, si está configurado.
      if (env.RATE_LIMITER) {
        const clave = request.headers.get("CF-Connecting-IP") || "desconocida";
        const { success } = await env.RATE_LIMITER.limit({ key: clave });
        if (!success) return error(429, "Demasiadas peticiones al Worker");
      }

      // 3) Verificar secretos.
      if (esAudd && !env.AUDD_API_TOKEN) {
        console.error("Falta el secreto AUDD_API_TOKEN");
        return error(500, "Servicio no disponible");
      }
      if (esGemini && !env.GEMINI_API_KEY) {
        console.error("Falta el secreto GEMINI_API_KEY");
        return error(500, "Servicio no disponible");
      }

      return esAudd
        ? await reconocerMusica(request, env)
        : await consultarGemini(request, env, ruta);
    } catch (e) {
      console.error("Error interno del Worker:", e);
      return error(500, "Error interno del servicio");
    }
  }
};
