// Compte le dépôt et écrit `frontend/public/project-stats.json`, lu par la page /projet.
// Lancé au déploiement (`npm run stats`, voir deploy-up.yml) ; on compte les sources, on ne lance aucun test.
// Sans dépendance. `GITHUB_TOKEN` et `GITHUB_REPOSITORY` : nombre de PR fusionnées (champ omis si absent ou en échec).
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SKIPPED_DIRS = new Set(['node_modules', 'target', 'dist', '.angular', '.git']);

function walk(dir, keep) {
  if (!existsSync(dir)) return [];
  const files = [];
  for (const name of readdirSync(dir)) {
    if (SKIPPED_DIRS.has(name)) continue;
    const path = join(dir, name);
    if (statSync(path).isDirectory()) files.push(...walk(path, keep));
    else if (keep(name)) files.push(path);
  }
  return files;
}

const read = (path) => readFileSync(path, 'utf8');
const sum = (numbers) => numbers.reduce((a, b) => a + b, 0);
const countMatches = (files, regex) => sum(files.map((f) => (read(f).match(regex) ?? []).length));
const countLines = (files) =>
  sum(
    files.map(
      (f) =>
        read(f)
          .split('\n')
          .filter((l) => l.trim() !== '').length,
    ),
  );

const JAVA_TEST = /^\s*@Test\b/gm;
const JS_CASE = /^\s*(?:it|test)(?:\.(?:only|skip|fixme))?\(/gm;

/** Dossiers `services/<nom>` qui sont des projets Maven. */
function serviceDirs(root) {
  const base = join(root, 'services');
  if (!existsSync(base)) return [];
  return readdirSync(base)
    .map((name) => join(base, name))
    .filter((dir) => existsSync(join(dir, 'pom.xml')));
}

/** Entrées du journal : puces de premier niveau de la section « Décisions » de architecture.md. */
function countDecisions(root) {
  const file = join(root, '.claude', 'rules', 'architecture.md');
  if (!existsSync(file)) return 0;
  let inSection = false;
  let count = 0;
  for (const line of read(file).split('\n')) {
    if (/^## /.test(line)) inSection = /^## Décisions\s*$/.test(line);
    else if (inSection && line.startsWith('- ')) count += 1;
  }
  return count;
}

function git(root, ...args) {
  try {
    return execFileSync('git', args, {
      cwd: root,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
  } catch {
    return undefined;
  }
}

/** Statistiques tirées des fichiers et de git (tout sauf GitHub). */
export function collectStats(root) {
  const services = serviceDirs(root);
  const front = join(root, 'frontend');
  const isSpec = (n) => n.endsWith('.spec.ts');
  const javaTests = services.flatMap((s) =>
    walk(join(s, 'src', 'test'), (n) => n.endsWith('.java')),
  );
  const javaMain = services.flatMap((s) =>
    walk(join(s, 'src', 'main'), (n) => n.endsWith('.java')),
  );
  const migrations = services.flatMap((s) =>
    walk(join(s, 'src', 'main', 'resources', 'db', 'migration'), (n) => n.endsWith('.sql')),
  );
  const vitest = walk(join(front, 'src'), isSpec);
  const playwright = [
    ...walk(join(front, 'e2e'), isSpec),
    ...walk(join(front, 'e2e-image'), isSpec),
  ];
  const ts = walk(
    join(front, 'src'),
    (n) => n.endsWith('.ts') && !isSpec(n) && !n.endsWith('.d.ts') && !n.includes('.generated.'),
  );
  const commits = Number(git(root, 'rev-list', '--count', 'HEAD'));
  const commit = git(root, 'rev-parse', '--short', 'HEAD');

  return {
    services: services.length + (existsSync(join(front, 'package.json')) ? 1 : 0),
    javaTests: countMatches(javaTests, JAVA_TEST),
    vitestCases: countMatches(vitest, JS_CASE),
    playwrightCases: countMatches(playwright, JS_CASE),
    javaLines: countLines(javaMain),
    typescriptLines: countLines(ts),
    migrations: migrations.length,
    contracts: walk(join(root, 'contracts'), (n) => n.endsWith('.json')).length,
    decisions: countDecisions(root),
    ...(Number.isFinite(commits) ? { commits } : {}),
    generatedAt: new Date().toISOString(),
    ...(commit ? { commit } : {}),
  };
}

/** Nombre de PR fusionnées (API de recherche GitHub) ; `undefined` sans jeton ni dépôt, ou en cas d'échec. */
export async function fetchMergedPullRequests(env, fetchFn = fetch) {
  const { GITHUB_TOKEN: token, GITHUB_REPOSITORY: repo } = env;
  if (!token || !repo) return undefined;
  try {
    const query = encodeURIComponent(`repo:${repo} is:pr is:merged`);
    const response = await fetchFn(`https://api.github.com/search/issues?q=${query}&per_page=1`, {
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json' },
    });
    if (!response.ok) return undefined;
    const total = (await response.json()).total_count;
    return Number.isInteger(total) ? total : undefined;
  } catch {
    return undefined;
  }
}

/** Calcule les chiffres et écrit le JSON ; rend les chiffres. */
export async function generate({ root, out, env = process.env, fetchFn = fetch }) {
  const stats = collectStats(root);
  const mergedPullRequests = await fetchMergedPullRequests(env, fetchFn);
  const result = { ...stats, ...(mergedPullRequests === undefined ? {} : { mergedPullRequests }) };
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, JSON.stringify(result, null, 2) + '\n');
  return result;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
  const out = join(root, 'frontend', 'public', 'project-stats.json');
  const result = await generate({ root, out });
  console.log(
    `project-stats.json écrit (${Object.keys(result).length} champs, commit ${result.commit ?? 'inconnu'})`,
  );
}
