#!/bin/bash
set -euxo pipefail
cat >/etc/sysctl.d/20-nat.conf <<'SYS'
net.ipv4.ip_forward=1
SYS
sysctl --system
# NetworkManager and iptables-persistent defaults differ across Amazon Linux revisions.
dnf install -y iptables-services
iptables -t nat -A POSTROUTING -o "$(ip -o -4 route show to default | awk '{print $5; exit}')" -j MASQUERADE
service iptables save
systemctl enable --now iptables
