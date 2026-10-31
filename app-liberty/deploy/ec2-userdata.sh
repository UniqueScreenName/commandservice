#!/bin/bash
# EC2 user data for Amazon Linux 2023: installs Java 8 and runs the Open Liberty runnable jar.
# Prereq: s3 cp app-liberty/target/command-app-liberty.jar s3://<BUCKET>/
#         and an instance profile with s3:GetObject on it.
set -ex

BUCKET="<BUCKET>"

dnf install -y java-1.8.0-amazon-corretto-headless
useradd -r -s /sbin/nologin liberty || true

mkdir -p /opt/command-app /var/lib/command-app
aws s3 cp "s3://${BUCKET}/command-app-liberty.jar" /opt/command-app/command-app-liberty.jar
chown -R liberty:liberty /opt/command-app /var/lib/command-app

cat > /etc/systemd/system/command-app.service <<'UNIT'
[Unit]
Description=Open Liberty command-app
After=network-online.target

[Service]
User=liberty
Environment=WLP_USER_DIR=/var/lib/command-app
ExecStart=/usr/bin/java -jar /opt/command-app/command-app-liberty.jar
Restart=on-failure
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable --now command-app
# Open TCP 9080 in the security group. Health check: GET /command-app/health
