import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { collectStats, fetchMergedPullRequests, generate } from '../../scripts/project-stats.mjs';

let root: string;

function put(path: string, content: string) {
  const file = join(root, path);
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, content);
}

function git(...args: string[]) {
  execFileSync('git', ['-c', 'user.name=t', '-c', 'user.email=t@t', ...args], {
    cwd: root,
    stdio: 'ignore',
  });
}

function buildFakeRepo() {
  for (const s of ['alpha-service', 'beta-service']) {
    put(`services/${s}/pom.xml`, '<project/>');
  }
  put('services/notes/readme.md', 'pas un service');
  put('services/alpha-service/src/main/java/A.java', 'class A {\n\n  int x;\n}\n');
  put('services/beta-service/src/main/java/B.java', 'class B {}\n');
  put('services/beta-service/src/main/java/target/Ignored.java', 'class I {}\n');
  put(
    'services/alpha-service/src/test/java/ATest.java',
    '  @Test\n  void a() {}\n  @Test\n  void b() {}\n  @ParameterizedTest\n  void c() {}\n',
  );
  put('services/beta-service/src/test/java/BTest.java', '@Test void b() {}\n');
  put('services/alpha-service/src/main/resources/db/migration/V1__a.sql', 'select 1;');
  put('services/alpha-service/src/main/resources/db/migration/V2__b.sql', 'select 2;');
  put('contracts/scan-event.v1.schema.json', '{}');
  put('contracts/scan-event.v2.schema.json', '{}');
  put('frontend/package.json', '{}');
  put('frontend/src/app/app.ts', 'export const a = 1;\n\nexport const b = 2;\n');
  put('frontend/src/app/types.d.ts', 'declare const x: number;\n');
  put(
    'frontend/src/app/app.spec.ts',
    "describe('x', () => {\n  it('a', () => {});\n  it.skip('b', () => {});\n  test('c', () => {});\n});\n",
  );
  put(
    'frontend/e2e/a.spec.ts',
    "test.describe('s', () => {\n  test('one', async () => {});\n  test('two', async () => {});\n});\n",
  );
  put('frontend/node_modules/lib/x.spec.ts', "it('ignored', () => {});\n");
  put(
    '.claude/rules/architecture.md',
    '# T\n\n## Décisions\n- Premier\n  - sous-point\n- Second\n\n## Structure\n- hors section\n',
  );
  git('init', '-q');
  git('add', '.');
  git('commit', '-q', '-m', 'un');
  git('commit', '-q', '--allow-empty', '-m', 'deux');
}

describe('project-stats', () => {
  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), 'project-stats-'));
    buildFakeRepo();
  });
  afterEach(() => rmSync(root, { recursive: true, force: true }));

  it('compte les sources d’un faux dépôt', () => {
    const stats = collectStats(root);
    expect(stats).toMatchObject({
      services: 3, // deux services Maven + le front
      javaTests: 3,
      vitestCases: 3,
      playwrightCases: 2,
      javaLines: 4, // lignes vides et dossier target exclus
      typescriptLines: 2, // ni spec ni .d.ts
      migrations: 2,
      contracts: 2,
      decisions: 2,
      commits: 2,
    });
    expect(stats.commit).toMatch(/^[0-9a-f]{7,}$/);
    expect(new Date(stats.generatedAt).getTime()).not.toBeNaN();
  });

  it('écrit le JSON, sans mergedPullRequests quand GITHUB_TOKEN est absent', async () => {
    const out = join(root, 'frontend', 'public', 'project-stats.json');
    await generate({ root, out, env: {} });
    const written = JSON.parse(readFileSync(out, 'utf8'));
    expect(written.services).toBe(3);
    expect('mergedPullRequests' in written).toBe(false);
  });

  it('ajoute les PR fusionnées lues sur GitHub avec le jeton', async () => {
    let seen: { url: string; init?: unknown } | undefined;
    const fetchFn = async (url: string, init?: unknown) => {
      seen = { url, init };
      return { ok: true, json: async () => ({ total_count: 42 }) };
    };
    const env = { GITHUB_TOKEN: 'secret', GITHUB_REPOSITORY: 'o/r' };
    const out = join(root, 'out', 'stats.json');
    const result = await generate({ root, out, env, fetchFn });
    expect(result.mergedPullRequests).toBe(42);
    expect(decodeURIComponent(seen!.url)).toContain('repo:o/r is:pr is:merged');
    expect(JSON.stringify(seen!.init)).toContain('Bearer secret');
  });

  it('omet le champ quand GitHub répond en erreur ou que la requête échoue', async () => {
    const env = { GITHUB_TOKEN: 'secret', GITHUB_REPOSITORY: 'o/r' };
    expect(
      await fetchMergedPullRequests(env, async () => ({ ok: false, json: async () => ({}) })),
    ).toBeUndefined();
    expect(
      await fetchMergedPullRequests(env, async () => {
        throw new Error('réseau');
      }),
    ).toBeUndefined();
  });
});
