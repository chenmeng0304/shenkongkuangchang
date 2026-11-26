#!/bin/bash
# 测试服务器到 GitHub 的 git 协议连通性
cd /tmp/skkc || exit 1
export GIT_TERMINAL_PROMPT=0
timeout 25 git ls-remote https://github.com/chenmeng0304/shenkongkuangchang.git > /tmp/lsr.txt 2>&1
echo "exit=$?"
echo "--- 输出 ---"
head -5 /tmp/lsr.txt
