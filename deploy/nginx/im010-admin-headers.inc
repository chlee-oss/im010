# 백오피스 보안 헤더 — /etc/nginx/conf.d/im010-admin-headers.inc
# nginx 는 location 에 add_header 가 하나라도 있으면 server 의 add_header 를 물려받지 않으므로, 그런 location 마다 다시 include 한다.
add_header Strict-Transport-Security "max-age=31536000" always;
add_header X-Frame-Options DENY always;
add_header X-Content-Type-Options nosniff always;
add_header Referrer-Policy no-referrer always;
add_header X-Robots-Tag "noindex, nofollow" always;
