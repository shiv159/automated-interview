#!/bin/sh
set -eu

: "${BACKEND_UPSTREAM_URL:=http://backend:8080}"
envsubst '${BACKEND_UPSTREAM_URL}' < /etc/nginx/conf.d/default.conf > /etc/nginx/conf.d/default.conf.rendered
mv /etc/nginx/conf.d/default.conf.rendered /etc/nginx/conf.d/default.conf
