# 고객 화면 보안 헤더 — /etc/nginx/conf.d/im010-headers.inc
# nginx 는 location 에 add_header 가 하나라도 있으면 server 의 add_header 를 물려받지 않으므로, 그런 location 마다 다시 include 한다.
# HSTS 에 includeSubDomains 는 넣지 않는다 (다른 하위 도메인 영향 방지)
add_header Strict-Transport-Security "max-age=31536000" always;
add_header X-Frame-Options SAMEORIGIN always;
add_header X-Content-Type-Options nosniff always;
add_header Referrer-Policy strict-origin-when-cross-origin always;
