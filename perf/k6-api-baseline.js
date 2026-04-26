import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const USERNAME = __ENV.USERNAME || 'zhangsan';
const PASSWORD = __ENV.PASSWORD || '123456';

export const options = {
  vus: Number(__ENV.VUS || 30),
  duration: __ENV.DURATION || '60s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
  },
};

function extractSessionCookie(setCookieHeader) {
  if (!setCookieHeader) {
    return '';
  }
  const match = String(setCookieHeader).match(/JSESSIONID=[^;]+/);
  return match ? match[0] : '';
}

export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(loginRes, { 'login status 200': (r) => r.status === 200 });

  return {
    cookie: extractSessionCookie(loginRes.headers['Set-Cookie']),
  };
}

export default function (data) {
  const res = http.get(
    `${BASE_URL}/api/appointments/availability?department=内科&date=2026-04-20&time=MORNING&doctorName=张医生`,
    {
      headers: {
        Cookie: data.cookie,
      },
    }
  );

  check(res, { 'availability status 200': (r) => r.status === 200 });
  sleep(0.1);
}
