# SPDX-FileCopyrightText: Copyright (c) 2026 Jason Perlow. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#
# Enable RDP backend for Weston — allows remote desktop via Windows App
# / Microsoft Remote Desktop client. Requires freerdp (pulled via recipe).

PACKAGECONFIG:append = " rdp"
