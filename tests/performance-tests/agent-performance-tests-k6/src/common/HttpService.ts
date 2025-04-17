
import http, { RefinedResponse, ResponseType, RequestBody } from 'k6/http'
import { check } from 'k6'
import { Counter } from 'k6/metrics'

export const statusChangeTimeouts = new Counter('status_change_timeouts')
/**
 * HttpService provides convenience methods for making HTTP requests using the k6 library.
 *
 * - reduces boilerplate code
 * - adds basic HTTP status code checks
 * - adds API key header
 * - adds content-type header
 * - adds base URL to all requests
 */
export class HttpService {
  private readonly baseUrl: string
  private readonly apiKey: string
  private readonly params: { headers: { [key: string]: string } }

  /**
   * Constructs a new HttpService instance with the specified base URL and API key.
   * @param baseUrl The base URL for the API.
   * @param apiKey The API key to use for authentication.
   */
  constructor (baseUrl: string, apiKey: string) {
    this.baseUrl = baseUrl
    this.apiKey = apiKey
    this.params = {
      headers: {
        'content-type': 'application/json',
        Accept: 'application/json',
        apikey: this.apiKey
      }
    }
  }

  public toJson (response: RefinedResponse<ResponseType>): any {
    return JSON.parse(response.body as string)
  }

  /**
   * Performs an HTTP POST request to the specified endpoint with the provided payload.
   * @param endpoint The API endpoint to post to.
   * @param payload The payload to include in the request body.
   * @param expectedStatus The expected HTTP status code for the response.
   * @returns An object containing the response status code and body
   */
  public post (
    endpoint: string,
    payload: string | RequestBody | null | undefined = null,
    expectedStatus: number = 201
  ): RefinedResponse<ResponseType> {
    if (typeof payload !== 'string') {
      payload = JSON.stringify(payload)
    }
    const res = http.post(
      `${this.baseUrl}/${endpoint}`,
      payload,
      this.params
    )
    check(res, {
      [`status is ${expectedStatus}`]: (r) => r.status === expectedStatus
    })
    return res
  }

  /**
   * Performs an HTTP GET request to the specified endpoint.
   * @param endpoint The API endpoint to get.
   * @param expectedStatus The expected HTTP status code for the response.
   * @returns An object containing the response status code and body
   */
  public get (endpoint: string, expectedStatus: number = 200): RefinedResponse<ResponseType> {
    const res = http.get(`${this.baseUrl}/${endpoint}`, this.params)
    check(res, {
      [`status is ${expectedStatus}`]: (r) => r.status === expectedStatus
    })
    return res
  }

  /**
   * Performs an HTTP PATCH request to the specified endpoint with the provided payload.
   * @param endpoint The API endpoint to patch.
   * @param payload The payload to include in the request body.
   * @param expectedStatus The expected HTTP status code for the response.
   * @returns An object containing the response status code and body
   */
  public patch (
    endpoint: string,
    payload: string | RequestBody | null | undefined = null,
    expectedStatus: number = 200
  ): RefinedResponse<ResponseType> {
    if (typeof payload !== 'string') {
      payload = JSON.stringify(payload)
    }
    const res = http.patch(
      `${this.baseUrl}/${endpoint}`,
      payload,
      this.params
    )
    check(res, {
      [`status is ${expectedStatus}`]: (r) => r.status === expectedStatus
    })
    return res
  }

  /**
   * Performs an HTTP DELETE request to the specified endpoint.
   * @param endpoint The API endpoint to delete.
   * @param expectedStatus The expected HTTP status code for the response.
   * @returns An object containing the response status code and an empty object.
   */
  public del (endpoint: string, expectedStatus: number = 200): RefinedResponse<ResponseType> {
    const res = http.del(`${this.baseUrl} / ${endpoint}`, null, this.params)
    check(res, {
      [`status is ${expectedStatus}`]: (r) => r.status === expectedStatus
    })
    return res
  }
}
