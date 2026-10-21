import json, math, re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "resources" / "svg"
OUT = ROOT / "android/app/src/main/res/drawable"
MANIFEST = ROOT / "android/app/src/main/assets/wiliwili_icons_manifest.json"
SVG = "{http://www.w3.org/2000/svg}"
XLINK = "{http://www.w3.org/1999/xlink}href"
NAMED = {"white":"#ffffffff","black":"#ff000000","none":"none","transparent":"#00000000"}

def local(tag):
    return tag.rsplit("}", 1)[-1]

def style_map(value):
    out = {}
    for item in (value or "").split(";"):
        if ":" in item:
            k, v = item.split(":", 1)
            out[k.strip().lower()] = v.strip()
    return out

def color(value, opacity=1.0):
    if value is None:
        return None
    raw = value.strip().lower()
    if raw.startswith("url("):
        return raw
    m = re.match(r"rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)", raw)
    if m:
        r, g, b = (max(0, min(255, int(float(m.group(i))))) for i in range(1, 4))
        a = float(m.group(4)) if m.group(4) else 1.0
        return "#%02X%02X%02X%02X" % (round(255*a*opacity), r, g, b)
    raw = NAMED.get(raw, raw)
    if raw == "none":
        return "none"
    if not raw.startswith("#"):
        return "#FF000000"
    h = raw[1:]
    if len(h) == 3:
        h = "".join(c*2 for c in h)
    elif len(h) == 4:
        h = "".join(c*2 for c in h)
        h = h[6:8] + h[0:6]
    if len(h) == 6:
        h = "ff" + h
    if len(h) != 8:
        return None
    a = round(int(h[:2], 16) * max(0.0, min(1.0, opacity)))
    return "#%02X" % a + h[2:].upper()

def css_rules(root):
    rules = []
    for el in root.iter():
        if local(el.tag) != "style":
            continue
        for sel, body in re.findall(r"([^{}]+)\{([^{}]*)\}", "".join(el.itertext())):
            classes = [p.strip()[1:] for p in sel.split(",")[0].split(".") if p.strip().startswith(".")]
            if classes:
                rules.append((classes, style_map(body)))
    return rules

def attrs_for(el, inherited, css):
    attrs = dict(inherited)
    classes = (el.get("class") or "").split()
    for needed, body in css:
        if classes and all(c in classes for c in needed):
            attrs.update(body)
    attrs.update({k:v for k,v in el.attrib.items() if k not in ("class", "style")})
    attrs.update(style_map(el.get("style")))
    return attrs

def fmt(v):
    raw = re.sub(r"[^0-9.eE+-]", "", str(v)) or "0"
    return ("%.4f" % float(raw)).rstrip("0").rstrip(".")

def shape_path(el, a):
    tag = local(el.tag)
    if tag == "path":
        return a.get("d")
    if tag == "rect":
        x, y = float(a.get("x", 0)), float(a.get("y", 0))
        w, h = float(a.get("width", 0)), float(a.get("height", 0))
        return "M%s,%s H%s V%s H%s Z" % (fmt(x), fmt(y), fmt(x+w), fmt(y+h), fmt(x))
    if tag in ("circle", "ellipse"):
        cx, cy = float(a.get("cx", 0)), float(a.get("cy", 0))
        rx = float(a.get("r", 0)) if tag == "circle" else float(a.get("rx", 0))
        ry = float(a.get("r", 0)) if tag == "circle" else float(a.get("ry", 0))
        return "M%s,%s a%s,%s 0 1,0 %s,0 a%s,%s 0 1,0 -%s,0 Z" % (
            fmt(cx-rx), fmt(cy), fmt(rx), fmt(ry), fmt(2*rx), fmt(rx), fmt(ry), fmt(2*rx))
    if tag == "line":
        return "M%s,%s L%s,%s" % (a.get("x1",0), a.get("y1",0), a.get("x2",0), a.get("y2",0))
    if tag in ("polygon", "polyline"):
        pts = re.findall(r"-?[\d.]+", a.get("points", ""))
        if len(pts) < 4:
            return None
        data = "M%s,%s " % (pts[0], pts[1]) + " ".join("L%s,%s" % (pts[i], pts[i+1]) for i in range(2, len(pts)-1, 2))
        return data + (" Z" if tag == "polygon" else "")
    return None

def group_xml(transform):
    if not transform:
        return []
    vals = [float(x) for x in re.findall(r"-?[\d.eE+]+", transform)]
    if transform.strip().startswith("matrix") and len(vals) >= 6:
        a, b, c, d, e, f = vals[:6]
        scale_x = math.hypot(a, b)
        scale_y = math.hypot(c, d)
        rot = math.degrees(math.atan2(b, a))
        return ['<group android:translateX="%s" android:translateY="%s" android:scaleX="%s" android:scaleY="%s" android:rotation="%s">' % (
            fmt(e), fmt(f), fmt(scale_x), fmt(scale_y), fmt(rot))]
    if transform.strip().startswith("translate") and len(vals) >= 2:
        return ['<group android:translateX="%s" android:translateY="%s">' % (fmt(vals[0]), fmt(vals[1]))]
    if transform.strip().startswith("scale") and len(vals) >= 1:
        return ['<group android:scaleX="%s" android:scaleY="%s">' % (fmt(vals[0]), fmt(vals[1] if len(vals)>1 else vals[0]))]
    if transform.strip().startswith("rotate") and len(vals) >= 1:
        return ['<group android:rotation="%s" android:pivotX="%s" android:pivotY="%s">' % (fmt(vals[0]), fmt(vals[1] if len(vals)>1 else 0), fmt(vals[2] if len(vals)>2 else 0))]
    return []

def grad_xml(grad, vbw, vbh):
    stops = []
    for st in grad:
        if local(st.tag) != "stop":
            continue
        raw_off = str(st.get("offset", "0"))
        off = float(raw_off.replace("%", "")) / (100 if "%" in raw_off else 1)
        css = style_map(st.get("style"))
        c = color(st.get("stop-color") or css.get("stop-color") or "#000", float(st.get("stop-opacity", 1)))
        if c:
            stops.append((off, c))
    if not stops:
        return None
    units = grad.get("gradientUnits", "objectBoundingBox")
    x1, y1 = float(grad.get("x1", 0)), float(grad.get("y1", 0))
    x2, y2 = float(grad.get("x2", 1)), float(grad.get("y2", 0))
    if units == "objectBoundingBox":
        x1, x2 = x1*vbw, x2*vbw
        y1, y2 = y1*vbh, y2*vbh
    lin = ['<aapt:attr name="android:fillColor">', '<gradient android:type="linear" android:startX="%s" android:startY="%s" android:endX="%s" android:endY="%s">' % (
        fmt(x1), fmt(y1), fmt(x2), fmt(y2))]
    for off, c in stops:
        lin.append('<item android:offset="%s" android:color="%s" />' % (fmt(off), c))
    lin.extend(['</gradient>', '</aapt:attr>'])
    return lin

def path_xml(a, path_data, grads, vbw, vbh):
    fill = color(a.get("fill", "#000000"), float(a.get("opacity", a.get("fill-opacity", 1))))
    stroke = color(a.get("stroke"), float(a.get("opacity", a.get("stroke-opacity", 1))))
    if not path_data or (fill in (None, "none") and stroke in (None, "none")):
        return []
    out = []
    if fill and fill.startswith("url("):
        gid = fill[5:-1].lstrip("#")
        g = grads.get(gid)
        if g is not None:
            out.append("<path android:pathData=\"%s\"%s>" % (path_data, ' android:fillType="evenOdd"' if a.get("fill-rule") == "evenodd" else ""))
            out.extend(grad_xml(g, vbw, vbh) or [])
            out.append("</path>")
            return out
        fill = "#FF000000"
    at = ['android:pathData="%s"' % path_data]
    if fill and fill != "none":
        at.append('android:fillColor="%s"' % fill)
    if a.get("fill-rule") == "evenodd":
        at.append('android:fillType="evenOdd"')
    if stroke and stroke != "none":
        at.append('android:strokeColor="%s"' % stroke)
        at.append('android:strokeWidth="%s"' % fmt(a.get("stroke-width", 1)))
        cap = {"round":"round","square":"square","butt":"butt"}.get(a.get("stroke-linecap"), "butt")
        join = {"round":"round","bevel":"bevel","miter":"miter"}.get(a.get("stroke-linejoin"), "miter")
        at.append('android:strokeLineCap="%s"' % cap)
        at.append('android:strokeLineJoin="%s"' % join)
    out.append("<path %s />" % " ".join(at))
    return out

def walk(el, inherited, css, grads, vbw, vbh, ids, depth=0):
    tag = local(el.tag)
    if depth > 12 or tag in ("defs", "style", "linearGradient", "radialGradient", "filter", "mask", "clipPath"):
        return []
    a = attrs_for(el, inherited, css)
    out = []
    if tag == "use":
        href = el.get(XLINK) or el.get("href") or ""
        ref = ids.get(href.lstrip("#"))
        x, y = float(el.get("x", 0)), float(el.get("y", 0))
        if ref is None:
            return out
        if x or y:
            out.append('<group android:translateX="%s" android:translateY="%s">' % (fmt(x), fmt(y)))
        out.extend(walk(ref, a, css, grads, vbw, vbh, ids, depth+1))
        if x or y:
            out.append("</group>")
        return out
    if tag in ("g", "svg"):
        out.extend(group_xml(a.get("transform")))
        for child in list(el):
            out.extend(walk(child, a, css, grads, vbw, vbh, ids, depth+1))
        if a.get("transform"):
            out.append("</group>")
        return out
    data = shape_path(el, a)
    if data:
        out.extend(group_xml(a.get("transform")))
        out.extend(path_xml(a, data, grads, vbw, vbh))
        if a.get("transform"):
            out.append("</group>")
    return out

def convert_file(path):
    root = ET.parse(path).getroot()
    ids = {}
    grads = {}
    for el in root.iter():
        if el.get("id"):
            ids[el.get("id")] = el
        if local(el.tag) in ("linearGradient", "radialGradient"):
            grads[el.get("id")] = el
    vb = (root.get("viewBox") or "").split()
    if len(vb) == 4:
        vbw, vbh = float(vb[2]), float(vb[3])
    else:
        vbw = float(re.sub(r"[^0-9.]", "", root.get("width") or "24") or 24)
        vbh = float(re.sub(r"[^0-9.]", "", root.get("height") or "24") or 24)
    css = css_rules(root)
    body = walk(root, {}, css, grads, vbw, vbh, ids)
    name = "wiliwili_" + re.sub(r"[^a-z0-9]+", "_", path.stem.lower()).strip("_")
    uses_grad = any("aapt:attr" in line for line in body)
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"%s' % (' xmlns:aapt="http://schemas.android.com/aapt"' if uses_grad else ""),
             '    android:width="%sdp" android:height="%sdp"' % (fmt(vbw), fmt(vbh)),
             '    android:viewportWidth="%s" android:viewportHeight="%s">' % (fmt(vbw), fmt(vbh))]
    lines.extend("    " + line for line in body)
    lines.append("</vector>")
    return name, "\n".join(lines) + "\n"

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    manifest = {}
    for path in sorted(SRC.glob("*.svg")):
        name, xml = convert_file(path)
        manifest[path.name] = name
        (OUT / (name + ".xml")).write_text(xml, encoding="utf-8")
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("converted %d icons" % len(manifest))

if __name__ == "__main__":
    main()
