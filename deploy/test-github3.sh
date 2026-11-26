#!/bin/bash
# 绕过 DNS，直接试几个已知 GitHub IP，看哪个能通
for ip in 140.82.112.3 140.82.113.3 140.82.114.3 20.205.243.166 20.27.177.113; do
  code=$(curl -s -o /dev/null -w '%{http_code}' -m 8 --resolve "github.com:443:$ip" https://github.com)
  echo "$ip -> http=$code"
done
echo "== git 端点（用 140.82.112.3） =="
code=$(curl -s -o /dev/null -w '%{http_code}' -m 10 --resolve "github.com:443:140.82.112.3" \
  'https://github.com/chenmeng0304/shenkongkuangchang.git/info/refs?service=git-upload-pack')
echo "info/refs -> http=$code"
