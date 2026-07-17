#!/usr/bin/env python3
import math, subprocess, os

FRAMES = 12
W, H = 220, 260           # SVG canvas
OUT = "/tmp/runner_frames"
os.makedirs(OUT, exist_ok=True)

# Anchors (in canvas units)
HIP = (96, 150)
SHO = (112, 86)
HEAD = (122, 50)
HEAD_R = 24

THIGH = 56
SHIN  = 52
UPPER_ARM = 40
FORE_ARM  = 36

def limb_pts(origin, a_thigh, bend, l1, l2):
    kx = origin[0] + math.sin(a_thigh) * l1
    ky = origin[1] + math.cos(a_thigh) * l1
    a_shin = a_thigh - bend
    fx = kx + math.sin(a_shin) * l2
    fy = ky + math.cos(a_shin) * l2
    return (kx, ky), (fx, fy)

def thigh_angle(ph):  # fore(+)/aft(-)
    return math.sin(ph) * 0.85

def knee_bend(ph):
    recovery = 0.5 + 0.5 * math.sin(ph + math.pi/2)  # high tuck on forward recovery
    return 0.35 + recovery * 1.35

def arm_pts(origin, ph):
    """Running arm with clear fore/aft drive.

    Forward swing: upper arm comes forward, forearm folds up so the hand rises
    toward the chin (in front of the chest).
    Back swing: upper arm goes behind the torso, forearm extends so the hand
    trails down and back behind the hip.
    """
    swing = math.sin(ph)                 # -1 (back) .. +1 (forward)
    # All angles measured from straight-DOWN; positive rotates toward +x (the
    # direction the figure faces / runs). sin = x-component, cos = y (down +).
    #
    # Upper arm (shoulder -> elbow): drives the elbow forward+down on the
    # forward swing, back+down on the back swing.
    upper = swing * 1.15                  # ~+/-66 deg shoulder swing
    ex = origin[0] + math.sin(upper) * UPPER_ARM
    ey = origin[1] + math.cos(upper) * UPPER_ARM
    # Forearm (elbow -> hand): on the FORWARD swing the hand rises up-and-forward
    # toward the chin (fore ~ +2.6 rad => up & slightly +x). On the BACK swing the
    # hand trails back-and-down behind the hip (fore ~ -0.4 rad).
    fore = 1.1 + swing * 1.5
    hx = ex + math.sin(fore) * FORE_ARM
    hy = ey + math.cos(fore) * FORE_ARM
    return (ex, ey), (hx, hy)

def path(a, b, c=None):
    if c is None:
        return f'M{a[0]:.1f},{a[1]:.1f} L{b[0]:.1f},{b[1]:.1f}'
    return f'M{a[0]:.1f},{a[1]:.1f} L{b[0]:.1f},{b[1]:.1f} L{c[0]:.1f},{c[1]:.1f}'

for i in range(FRAMES):
    ph = (i / FRAMES) * 2 * math.pi
    # legs half a cycle apart
    fk, ff = limb_pts(HIP, thigh_angle(ph), knee_bend(ph), THIGH, SHIN)
    bk, bf = limb_pts(HIP, thigh_angle(ph+math.pi), knee_bend(ph+math.pi), THIGH, SHIN)
    # arms oppose legs (front arm swings opposite the front leg)
    fe, fh = arm_pts(SHO, ph + math.pi)
    be, bh = arm_pts(SHO, ph)

    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
  <defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#7ec2ff"/><stop offset="1" stop-color="#1a6bf0"/>
  </linearGradient></defs>
  <g stroke="url(#g)" fill="none" stroke-linecap="round" stroke-linejoin="round">
    <!-- back limbs -->
    <path d="{path(SHO, be, bh)}" stroke-width="16"/>
    <path d="{path(HIP, bk, bf)}" stroke-width="22"/>
    <!-- torso -->
    <path d="{path(SHO, HIP)}" stroke-width="30"/>
    <!-- front limbs -->
    <path d="{path(HIP, fk, ff)}" stroke-width="24"/>
    <path d="{path(SHO, fe, fh)}" stroke-width="17"/>
  </g>
  <circle cx="{HEAD[0]}" cy="{HEAD[1]}" r="{HEAD_R}" fill="url(#g)"/>
</svg>'''
    svg_path = f"{OUT}/runner_{i:02d}.svg"
    png_path = f"{OUT}/runner_{i:02d}.png"
    with open(svg_path, "w") as f:
        f.write(svg)
    subprocess.run(["rsvg-convert", "-w", str(W), "-h", str(H), svg_path, "-o", png_path], check=True)

print(f"Generated {FRAMES} frames in {OUT}")
