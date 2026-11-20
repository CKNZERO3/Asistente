import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const git = (...args) =>
  execFileSync('git', args, { encoding: 'utf8' }).trim();

const outDir = path.resolve('dist-mirror');
fs.mkdirSync(outDir, { recursive: true });

// 1. Generar snapshot puro del código actual (sin alterar líneas ni inyectar logs)
const repoXmlPath = path.join(outDir, 'repo-actual.xml');
const ignorar = [
  '.git/**',
  'dist-mirror/**',
  'build/**',
  'app/build/**',
  '.gradle/**',
  '.idea/**',
  'local.properties',
  '*.jks',
  '*.keystore',
  '.env*',
  '.dev.vars'
].join(',');

execFileSync(
  'npx',
  [
    '--yes',
    'repomix',
    '--style', 'xml',
    '--output-show-line-numbers',
    '--ignore', ignorar,
    '--output', repoXmlPath
  ],
  { stdio: 'inherit' }
);

// 2. Construir entrada inteligente con fecha, hora y detalle del commit actual
const hash = git('rev-parse', 'HEAD');
const shortHash = git('rev-parse', '--short', 'HEAD');
const rama = git('rev-parse', '--abbrev-ref', 'HEAD');
const autor = git('log', '-1', '--pretty=format:%an');
const mensaje = git('log', '-1', '--pretty=format:%s');

const fechaHora = new Intl.DateTimeFormat('es-VE', {
  timeZone: 'America/Caracas',
  dateStyle: 'full',
  timeStyle: 'medium'
}).format(new Date());

const clasificar = (msg) => {
  const m = msg.toLowerCase();
  if (/^(feat|add|nuevo|agreg|implement)/.test(m)) return 'Nuevas funciones';
  if (/^(fix|bug|correg|arregl|solucion|error|fallo)/.test(m)) return 'Correcciones';
  if (/^(refactor|clean|limpi|mejor|optimiz)/.test(m)) return 'Refactor y mejoras';
  if (/^(build|ci|gradle|workflow|deps|chore)/.test(m)) return 'Build, Gradle y CI';
  return 'Actualización';
};

const numstat = git('show', '--numstat', '--format=', hash);
const archivos = numstat
  .split('\n')
  .filter(Boolean)
  .map((linea) => {
    const [add, del, file] = linea.split('\t');
    return { file, add: add || '0', del: del || '0' };
  });

let diffRecortado = '';
try {
  const diffRaw = git('show', '--format=', '--unified=2', hash, '--', ':(exclude)*.lock');
  if (diffRaw) {
    diffRecortado = diffRaw.split('\n').slice(0, 180).join('\n');
  }
} catch {
  diffRecortado = '';
}

const nuevaEntrada = [
  `## [${fechaHora}] — \`${shortHash}\` (${clasificar(mensaje)})`,
  '',
  `- **Cambio:** ${mensaje}`,
  `- **Rama:** \`${rama}\` | **Autor:** ${autor} | **Commit:** \`${hash}\``,
  ''
];

if (archivos.length > 0) {
  nuevaEntrada.push('| Archivo modificado | Líneas |', '| :--- | :---: |');
  for (const a of archivos) {
    nuevaEntrada.push(`| \`${a.file}\` | +${a.add} / -${a.del} |`);
  }
  nuevaEntrada.push('');
}

if (diffRecortado) {
  nuevaEntrada.push('<details><summary>Ver diff técnico del commit</summary>', '', '```diff', diffRecortado, '```', '', '</details>', '');
}

// 3. Combinar con el historial previo sin duplicar commits ni inflar el archivo
const changelogPath = path.join(outDir, 'changelog-inteligente.md');
const encabezado = '# Registro Automático de Cambios — Repositorio Iris\n\n';
let historialPrevio = '';

if (fs.existsSync(changelogPath)) {
  const contenidoActual = fs.readFileSync(changelogPath, 'utf8');
  const sinCabecera = contenidoActual.replace(/^# Registro Automático de Cambios[^\n]*\n+/, '');
  if (!sinCabecera.includes(`\`${hash}\``)) {
    const bloques = sinCabecera.split('\n---\n').filter((b) => b.trim().length > 0);
    historialPrevio = bloques.slice(0, 24).join('\n---\n');
  } else {
    historialPrevio = sinCabecera;
  }
}

const bloquesFinales = historialPrevio.includes(`\`${hash}\``)
  ? historialPrevio
  : [nuevaEntrada.join('\n').trim(), historialPrevio].filter(Boolean).join('\n\n---\n\n');

fs.writeFileSync(changelogPath, `${encabezado}${bloquesFinales}\n`, 'utf8');
