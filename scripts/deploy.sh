#!/usr/bin/env sh

echo "[Deploying...]"
rsync -r * root@attentions.oskarth.com:~/attentions/
echo "[Done.]"
