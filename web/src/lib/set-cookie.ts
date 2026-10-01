/** The attributes this app copies over from api's Set-Cookie; typed here to avoid importing Next internals. */
export type CookieOptions = {
  path?: string;
  maxAge?: number;
  expires?: Date;
  sameSite?: "lax" | "strict" | "none";
  httpOnly?: boolean;
  secure?: boolean;
};

export type ParsedCookie = { name: string; value: string; options: CookieOptions };

/**
 * Reads one Set-Cookie header so that api's session cookie can be handed to the browser unchanged (ADR 0018).
 * Attributes that api does not send are left unset rather than defaulted, so nothing is invented here.
 */
export function parseSetCookie(setCookie: string): ParsedCookie | null {
  const [pair, ...attributes] = setCookie.split(";");
  const separator = pair.indexOf("=");
  if (separator < 1) {
    return null;
  }
  const options: CookieOptions = {};
  for (const attribute of attributes) {
    const [rawName, ...rest] = attribute.split("=");
    const value = rest.join("=").trim();
    switch (rawName.trim().toLowerCase()) {
      case "path":
        options.path = value;
        break;
      case "max-age":
        options.maxAge = Number(value);
        break;
      case "expires":
        options.expires = new Date(value);
        break;
      case "samesite":
        options.sameSite = value.toLowerCase() as CookieOptions["sameSite"];
        break;
      case "httponly":
        options.httpOnly = true;
        break;
      case "secure":
        options.secure = true;
        break;
    }
  }
  return { name: pair.slice(0, separator).trim(), value: pair.slice(separator + 1).trim(), options };
}
