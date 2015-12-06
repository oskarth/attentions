#!/usr/bin/env sh

echo "[Deploying...]"
rsync --exclude target/ -r * root@attentions.oskarth.com:~/attentions/
echo "[Done.]"
