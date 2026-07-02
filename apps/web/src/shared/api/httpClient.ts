type Fetcher = (input: string, init: RequestInit) => Promise<Response>

interface HttpClientOptions {
  baseUrl: string
  fetcher?: Fetcher
}

interface HttpClient {
  get<T>(path: string): Promise<T>
}

export function createHttpClient(options: HttpClientOptions): HttpClient {
  const fetcher = options.fetcher ?? fetch
  const baseUrl = options.baseUrl.replace(/\/$/, '')

  return {
    async get<T>(path: string): Promise<T> {
      const response = await fetcher(`${baseUrl}${path}`, {
        credentials: 'include',
        headers: { Accept: 'application/json' },
        method: 'GET',
      })

      if (!response.ok) {
        throw new Error(`请求失败：${response.status}`)
      }

      return response.json() as Promise<T>
    },
  }
}
