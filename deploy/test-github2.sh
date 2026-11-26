#!/bin/bash
# 逐项排查服务器到 GitHub 各路径的连通性
echo "== 1) github.com 首页 =="
curl -s -o /dev/null -w 'http=%{http_code} time=%{time_total}s\n' -m 10 https://github.com

echo "== 2) git smart-http 端点（公开仓库应返回 200/401） =="
curl -s -o /dev/null -w 'http=%{http_code} time=%{time_total}s\n' -m 15 \
  'https://github.com/chenmeng0304/shenkongkuangchang.git/info/refs?service=git-upload-pack'

echo "== 3) 仓库网页 =="
curl -s -o /dev/null -w 'http=%{http_code} time=%{time_total}s\n' -m 10 \
  https://github.com/chenmeng0304/shenkongkuangchang

echo "== 4) git 用的 TLS 版本 / 证书 =="
curl -s -o /dev/null -w 'http=%{http_code}\n' -m 10 --tlsv1.2 https://github.com

echo "== 5) 服务器 DNS =="
getent hosts github.com

echo "== 6) 环境里的代理变量 =="
env | grep -i proxy || echo '(无代理)'
