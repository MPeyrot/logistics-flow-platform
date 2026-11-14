export interface ProjectStatsFile {
  services: number;
  javaTests: number;
  vitestCases: number;
  playwrightCases: number;
  javaLines: number;
  typescriptLines: number;
  migrations: number;
  contracts: number;
  decisions: number;
  mergedPullRequests?: number;
  commits?: number;
  generatedAt: string;
  commit?: string;
}

type FetchFn = (url: string, init?: unknown) => Promise<{ ok: boolean; json(): Promise<unknown> }>;
type Env = Record<string, string | undefined>;

export function collectStats(root: string): ProjectStatsFile;
export function fetchMergedPullRequests(env: Env, fetchFn?: FetchFn): Promise<number | undefined>;
export function generate(options: {
  root: string;
  out: string;
  env?: Env;
  fetchFn?: FetchFn;
}): Promise<ProjectStatsFile>;
