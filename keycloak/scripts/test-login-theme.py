#!/usr/bin/env python3
"""Exercise the packaged login theme using real Keycloak HTTP responses."""

import argparse
import hashlib
import hmac
import struct
import time
from html.parser import HTMLParser
from http.cookiejar import CookieJar, DefaultCookiePolicy
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlencode, urljoin, urlparse
from urllib.request import HTTPRedirectHandler, HTTPCookieProcessor, build_opener


class Page(HTMLParser):
    def __init__(self, source):
        super().__init__()
        self.ids = {}
        self.styles = []
        self.favicons = []
        self.hidden = {}
        self.forms = []
        self.feed(source)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if "id" in attrs:
            self.ids[attrs["id"]] = attrs
        if tag == "input" and attrs.get("type") == "hidden" and attrs.get("name"):
            self.hidden[attrs["name"]] = attrs.get("value", "")
        if tag == "form":
            self.forms.append(attrs)
        if tag == "link" and attrs.get("rel") == "stylesheet":
            self.styles.append(attrs["href"])
        if tag == "link" and attrs.get("rel") == "icon":
            self.favicons.append(attrs["href"])


class LocalCallback(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Observe successful redirects without requesting a nonexistent app.
        if newurl.startswith("http://localhost/callback") or newurl.startswith("http://localhost/logout-complete"):
            return None
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def new_browser():
    # Browsers accept Secure cookies on loopback HTTP; urllib otherwise drops
    # Keycloak's restart cookie. The entry point below requires loopback.
    cookies = CookieJar(DefaultCookiePolicy(secure_protocols=("https", "http")))
    return build_opener(HTTPCookieProcessor(cookies), LocalCallback())


def fetch(browser, url, fields=None):
    data = urlencode(fields).encode() if fields is not None else None
    try:
        response = browser.open(url, data, timeout=15)
    except HTTPError as error:
        response = error
    with response:
        source = response.read().decode()
        page = Page(source)
        page.source = source
        page.status = response.status
        page.url = response.url
        page.location = response.headers.get("Location", "")
    return page


def themed(page):
    assert any(url.endswith("/css/grounds.css") for url in page.styles), "Response lost the Grounds theme"
    assert 'class="grounds-footer"' in page.source, "Shared footer did not render"


def post_form(browser, page, form_id, fields):
    form = page.ids[form_id] if form_id else page.forms[0]
    return fetch(browser, urljoin(page.url, form["action"]), {**page.hidden, **fields})


def check(base):
    assert urlparse(base).hostname in ("localhost", "127.0.0.1"), "Run only against an isolated loopback fixture"
    browser = new_browser()
    auth = base + "/realms/grounds/protocol/openid-connect/auth?" + urlencode({
        "client_id": "theme-smoke", "redirect_uri": "http://localhost/callback",
        "response_type": "code", "scope": "openid", "state": "theme-smoke",
    })
    page = fetch(browser, auth)
    assert "kc-form-login" in page.ids, "Keycloak login form was not rendered"
    css_url = next((url for url in page.styles if url.endswith("/css/grounds.css")), None)
    assert css_url, "Grounds login theme was not selected or packaged"
    assert page.favicons and page.favicons[0].endswith("/img/grounds-icon-32.png"), "Grounds favicon is missing"
    themed(page)
    with browser.open(urljoin(page.url, css_url), timeout=15) as response:
        assert response.status == 200
        assert "--grounds-canvas" in response.read().decode(), "Grounds stylesheet is missing"
    resources = urljoin(page.url, css_url).removesuffix("css/grounds.css")
    for name in ["img/grounds-logo-full.webp", "img/grounds-icon-32.png", "fonts/geist-latin.woff2", "fonts/geist-latin-ext.woff2"]:
        with browser.open(resources + name, timeout=15) as response:
            assert response.status == 200 and len(response.read()) > 100, f"Missing resource: {name}"
    assert urlparse(resources).netloc == urlparse(base).netloc, "Theme assets must be served by Keycloak"
    invalid = post_form(browser, page, "kc-form-login", {"username": "theme-user", "password": "incorrect"})
    assert "input-error-username" in invalid.ids, "Invalid credentials did not render accessible feedback"
    themed(invalid)

    otp = post_form(browser, invalid, "kc-form-login", {"username": "otp-user", "password": "theme-password"})
    assert "otp" in otp.ids, "Existing OTP credential did not trigger MFA"
    themed(otp)
    invalid_otp = post_form(browser, otp, "kc-otp-login-form", {"otp": "invalid"})
    assert "input-error-otp" in invalid_otp.ids, "Invalid OTP did not render accessible feedback"
    themed(invalid_otp)

    # Keycloak's imported OTP secret is the raw UTF-8 secret, not Base32.
    counter = struct.pack(">Q", int(time.time()) // 30)
    digest = hmac.new(b"theme-smoke-otp-secret", counter, hashlib.sha1).digest()
    offset = digest[-1] & 15
    code = str((struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7fffffff) % 1000000).zfill(6)
    signed_in = post_form(browser, invalid_otp, "kc-otp-login-form", {"otp": code})
    query = parse_qs(urlparse(signed_in.location).query)
    assert signed_in.status == 302 and query.get("code"), "Valid OTP did not complete authentication"

    logout = fetch(browser, base + "/realms/grounds/protocol/openid-connect/logout?" + urlencode({
        "client_id": "theme-smoke", "post_logout_redirect_uri": "http://localhost/logout-complete",
    }))
    assert "kc-logout-confirm" in logout.ids, "Logout confirmation did not render"
    themed(logout)
    logged_out = post_form(browser, logout, None, {"confirmLogout": ""})
    assert logged_out.status == 302 and logged_out.location == "http://localhost/logout-complete", "Logout did not complete"

    expired = fetch(new_browser(), base + "/realms/grounds/login-actions/authenticate?" + urlencode({
        "client_id": "theme-smoke", "tab_id": "expired",
    }))
    assert expired.ids["keycloak-bg"]["data-page-id"] in ("login-page-expired", "login-error"), "Expired authentication did not render a recovery page"
    themed(expired)
    error = fetch(new_browser(), auth.replace("client_id=theme-smoke", "client_id=missing-client"))
    assert error.status == 400 and "kc-error-message" in error.ids, "Invalid client did not render the error page"
    themed(error)
    german = fetch(new_browser(), auth + "&ui_locales=de")
    assert "Datenschutz" in german.source and "Nutzungsbedingungen" in german.source, "German footer translations did not render"
    themed(german)
    print("Grounds login theme: local assets, login/error, OTP/error/success, expired authentication, logout and German locale passed")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base_url")
    check(parser.parse_args().base_url.rstrip("/"))
