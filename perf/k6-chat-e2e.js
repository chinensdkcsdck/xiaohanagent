import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const USERNAME = __ENV.USERNAME || 'zhangsan';
const PASSWORD = __ENV.PASSWORD || '123456';

export const options = {
  vus: Number(__ENV.VUS || 5),
  duration: __ENV.DURATION || '60s',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<15000'],
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
  const memoryId = __VU * 100000 + __ITER;
  const payload = {
    memoryId: memoryId,
    message: '我想预约下周一上午内科，请告诉我可选时段',
  };

  const res = http.post(
    `${BASE_URL}/xiaohan/chat`,
    JSON.stringify(payload),
    {
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        Cookie: data.cookie,
      },
      timeout: '30s',
    }
  );

  check(res, {
    'chat status 200': (r) => r.status === 200,
    'chat body not empty': (r) => r.body && r.body.length > 0,
  });

  sleep(0.2);
}
