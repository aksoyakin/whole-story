import { describe, expect, it } from "vitest";
import { parseSetCookie } from "./set-cookie";

/** Copied verbatim from a running api, so the test breaks if the cookie's shape ever changes. */
const FROM_API =
  "SESSION=NjExZjdjNjEtYWUyZC00MjI4LTlhZTgtYTg1NGY0MGJjODg4; Max-Age=1209600; " +
  "Expires=Thu, 15 Oct 2026 18:05:50 GMT; Path=/; Secure; HttpOnly; SameSite=Lax";

describe("forwarding api's session cookie", () => {
  it("keeps every attribute that protects the cookie", () => {
    const parsed = parseSetCookie(FROM_API);

    expect(parsed).not.toBeNull();
    expect(parsed?.name).toBe("SESSION");
    expect(parsed?.value).toBe("NjExZjdjNjEtYWUyZC00MjI4LTlhZTgtYTg1NGY0MGJjODg4");
    expect(parsed?.options).toMatchObject({
      path: "/",
      maxAge: 1_209_600,
      secure: true,
      httpOnly: true,
      sameSite: "lax",
    });
    expect(parsed?.options.expires?.toISOString()).toBe("2026-10-15T18:05:50.000Z");
  });

  it("invents nothing: an attribute api did not send stays unset", () => {
    const parsed = parseSetCookie("SESSION=abc; Path=/");

    expect(parsed?.options.secure).toBeUndefined();
    expect(parsed?.options.httpOnly).toBeUndefined();
    expect(parsed?.options.sameSite).toBeUndefined();
    // No Domain is what keeps the cookie host-only, so it must not be filled in either.
    expect(parsed?.options).not.toHaveProperty("domain");
  });

  it("ignores a header that carries no cookie", () => {
    expect(parseSetCookie("nonsense")).toBeNull();
  });

  it("survives a value that contains an equals sign", () => {
    expect(parseSetCookie("SESSION=a=b==; Path=/")?.value).toBe("a=b==");
  });
});
