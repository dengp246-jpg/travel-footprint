from __future__ import annotations

import html
import json
import urllib.request
from pathlib import Path

SOURCE_URL = "https://raw.githubusercontent.com/apache/echarts/master/test/data/map/js/china.js"
OUTPUT_PATH = Path("src/main/resources/static/images/china-map-generated.svg")
PROVINCE_OUTPUT_DIR = Path("src/main/resources/static/images/provinces")
PROVINCE_METADATA_PATH = PROVINCE_OUTPUT_DIR / "metadata.json"

SVG_WIDTH = 1600
SVG_HEIGHT = 1200
CHINA_MIN_LONGITUDE = 73.5
CHINA_MAX_LONGITUDE = 134.8
CHINA_MIN_LATITUDE = 18.0
CHINA_MAX_LATITUDE = 53.8
SVG_MARGIN_LEFT = 70
SVG_MARGIN_RIGHT = 70
SVG_MARGIN_TOP = 80
SVG_MARGIN_BOTTOM = 100

DRAWABLE_WIDTH = SVG_WIDTH - SVG_MARGIN_LEFT - SVG_MARGIN_RIGHT
DRAWABLE_HEIGHT = SVG_HEIGHT - SVG_MARGIN_TOP - SVG_MARGIN_BOTTOM
LON_RANGE = CHINA_MAX_LONGITUDE - CHINA_MIN_LONGITUDE
LAT_RANGE = CHINA_MAX_LATITUDE - CHINA_MIN_LATITUDE
MAP_SCALE = min(DRAWABLE_WIDTH / LON_RANGE, DRAWABLE_HEIGHT / LAT_RANGE)
MAP_DRAW_WIDTH = LON_RANGE * MAP_SCALE
MAP_DRAW_HEIGHT = LAT_RANGE * MAP_SCALE
MAP_ORIGIN_X = SVG_MARGIN_LEFT + (DRAWABLE_WIDTH - MAP_DRAW_WIDTH) / 2
MAP_ORIGIN_Y = SVG_MARGIN_TOP + (DRAWABLE_HEIGHT - MAP_DRAW_HEIGHT) / 2
PROVINCE_PADDING = 48

PROVINCE_SLUGS = {
    "北京": "beijing",
    "天津": "tianjin",
    "河北": "hebei",
    "山西": "shanxi",
    "内蒙古": "inner-mongolia",
    "辽宁": "liaoning",
    "吉林": "jilin",
    "黑龙江": "heilongjiang",
    "上海": "shanghai",
    "江苏": "jiangsu",
    "浙江": "zhejiang",
    "安徽": "anhui",
    "福建": "fujian",
    "江西": "jiangxi",
    "山东": "shandong",
    "河南": "henan",
    "湖北": "hubei",
    "湖南": "hunan",
    "广东": "guangdong",
    "广西": "guangxi",
    "海南": "hainan",
    "重庆": "chongqing",
    "四川": "sichuan",
    "贵州": "guizhou",
    "云南": "yunnan",
    "西藏": "tibet",
    "陕西": "shaanxi",
    "甘肃": "gansu",
    "青海": "qinghai",
    "宁夏": "ningxia",
    "新疆": "xinjiang",
    "台湾": "taiwan",
    "香港": "hong-kong",
    "澳门": "macao",
}


def fetch_geojson() -> dict:
    with urllib.request.urlopen(SOURCE_URL, timeout=30) as response:
        content = response.read().decode("utf-8")
    start_marker = "const geoJSON = "
    end_marker = "const boundaryLineNameMap = {"
    start = content.find(start_marker)
    end = content.find(end_marker)
    if start == -1 or end == -1:
        raise RuntimeError("Unable to locate geoJSON payload in source file.")
    payload = content[start + len(start_marker):end].strip()
    if payload.endswith(";"):
        payload = payload[:-1]
    return json.loads(payload)


def project(longitude: float, latitude: float) -> tuple[float, float]:
    x = MAP_ORIGIN_X + (longitude - CHINA_MIN_LONGITUDE) * MAP_SCALE
    y = MAP_ORIGIN_Y + (CHINA_MAX_LATITUDE - latitude) * MAP_SCALE
    return round(x, 2), round(y, 2)


def short_name(name: str) -> str:
    replacements = [
        ("维吾尔自治区", ""),
        ("壮族自治区", ""),
        ("回族自治区", ""),
        ("特别行政区", ""),
        ("自治区", ""),
        ("省", ""),
        ("市", ""),
    ]
    result = name
    for old, new in replacements:
        result = result.replace(old, new)
    return result


def path_from_geometry(geometry: dict) -> str:
    geometry_type = geometry["type"]
    coordinates = geometry["coordinates"]
    polygons = coordinates if geometry_type == "MultiPolygon" else [coordinates]
    commands: list[str] = []
    for polygon in polygons:
        for ring in polygon:
            if not ring:
                continue
            first_x, first_y = project(ring[0][0], ring[0][1])
            commands.append(f"M {first_x} {first_y}")
            for longitude, latitude in ring[1:]:
                x, y = project(longitude, latitude)
                commands.append(f"L {x} {y}")
            commands.append("Z")
    return " ".join(commands)


def projected_rings(geometry: dict) -> list[list[list[tuple[float, float]]]]:
    geometry_type = geometry["type"]
    coordinates = geometry["coordinates"]
    polygons = coordinates if geometry_type == "MultiPolygon" else [coordinates]
    projected_polygons: list[list[list[tuple[float, float]]]] = []
    for polygon in polygons:
        projected_polygon: list[list[tuple[float, float]]] = []
        for ring in polygon:
            if not ring:
                continue
            projected_polygon.append([project(longitude, latitude) for longitude, latitude in ring])
        if projected_polygon:
            projected_polygons.append(projected_polygon)
    return projected_polygons


def bounds_from_projected(projected_polygons: list[list[list[tuple[float, float]]]]) -> tuple[float, float, float, float]:
    xs = [point[0] for polygon in projected_polygons for ring in polygon for point in ring]
    ys = [point[1] for polygon in projected_polygons for ring in polygon for point in ring]
    return min(xs), min(ys), max(xs), max(ys)


def path_from_projected(projected_polygons: list[list[list[tuple[float, float]]]], offset_x: float = 0.0, offset_y: float = 0.0) -> str:
    commands: list[str] = []
    for polygon in projected_polygons:
        for ring in polygon:
            if not ring:
                continue
            first_x, first_y = ring[0]
            commands.append(f"M {round(first_x + offset_x, 2)} {round(first_y + offset_y, 2)}")
            for x, y in ring[1:]:
                commands.append(f"L {round(x + offset_x, 2)} {round(y + offset_y, 2)}")
            commands.append("Z")
    return " ".join(commands)


def build_province_svg(name: str, label_name: str, label_x: float, label_y: float, projected_polygons: list[list[list[tuple[float, float]]]]) -> tuple[str, dict]:
    min_x, min_y, max_x, max_y = bounds_from_projected(projected_polygons)
    width = max_x - min_x
    height = max_y - min_y
    translated_path = path_from_projected(
        projected_polygons,
        offset_x=PROVINCE_PADDING - min_x,
        offset_y=PROVINCE_PADDING - min_y,
    )
    svg_width = round(width + PROVINCE_PADDING * 2, 2)
    svg_height = round(height + PROVINCE_PADDING * 2, 2)
    local_label_x = round(label_x - min_x + PROVINCE_PADDING, 2)
    local_label_y = round(label_y - min_y + PROVINCE_PADDING, 2)
    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {svg_width} {svg_height}" role="img" aria-labelledby="title desc">
  <title id="title">{html.escape(label_name)}地图</title>
  <desc id="desc">用于旅迹系统省份聚焦展示的{html.escape(label_name)}矢量地图。</desc>
  <defs>
    <filter id="outerGlow" x="-10%" y="-10%" width="120%" height="120%">
      <feDropShadow dx="0" dy="0" stdDeviation="5" flood-color="#8f8ada" flood-opacity="0.55"/>
    </filter>
  </defs>
  <style>
    .province-shadow {{ fill: #ffffff; stroke: #8f8ada; stroke-width: 4.8; filter: url(#outerGlow); }}
    .province-line {{ fill: #ffffff; stroke: #bdbdbd; stroke-width: 1.05; }}
    .province-label {{ font: 500 18px 'Microsoft YaHei', sans-serif; fill: #434343; text-anchor: middle; dominant-baseline: middle; }}
  </style>
  <rect width="{svg_width}" height="{svg_height}" fill="#ffffff"/>
  <path class="province-shadow" d="{translated_path}" />
  <path class="province-line" d="{translated_path}" />
  <text class="province-label" x="{local_label_x}" y="{local_label_y}">{html.escape(label_name)}</text>
</svg>
"""
    metadata = {
        "name": name,
        "label": label_name,
        "left": round(min_x / SVG_WIDTH * 100, 4),
        "top": round(min_y / SVG_HEIGHT * 100, 4),
        "width": round(width / SVG_WIDTH * 100, 4),
        "height": round(height / SVG_HEIGHT * 100, 4),
        "leftPx": round(min_x, 2),
        "topPx": round(min_y, 2),
        "widthPx": round(width, 2),
        "heightPx": round(height, 2),
        "assetWidthPx": svg_width,
        "assetHeightPx": svg_height,
        "paddingPx": PROVINCE_PADDING,
        "assetPath": f"/images/provinces/{PROVINCE_SLUGS[label_name]}.svg",
    }
    return svg, metadata


def build_svg(geojson: dict) -> str:
    province_paths: list[str] = []
    province_labels: list[str] = []
    province_metadata: dict[str, dict] = {}
    province_svgs: dict[str, str] = {}
    for feature in geojson["features"]:
        geometry = feature["geometry"]
        properties = feature["properties"]
        name = properties["name"]
        label_name = short_name(name)
        if label_name not in PROVINCE_SLUGS:
            continue
        projected_polygons = projected_rings(geometry)
        d = path_from_projected(projected_polygons)
        province_paths.append(
            f'<path class="province province-shadow" d="{d}" />\n'
            f'<path class="province province-line" d="{d}" />'
        )
        label_x, label_y = project(properties["lng"], properties["lat"])
        province_labels.append(
            f'<text class="province-label" x="{label_x}" y="{label_y}">{html.escape(label_name)}</text>'
        )
        province_svg, metadata = build_province_svg(name, label_name, label_x, label_y, projected_polygons)
        province_svgs[label_name] = province_svg
        province_metadata[label_name] = metadata

    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {SVG_WIDTH} {SVG_HEIGHT}" role="img" aria-labelledby="title desc">
  <title id="title">中国地图</title>
  <desc id="desc">基于中国省级边界数据生成的矢量中国地图，用于旅游足迹系统展示。</desc>
  <defs>
    <filter id="outerGlow" x="-10%" y="-10%" width="120%" height="120%">
      <feDropShadow dx="0" dy="0" stdDeviation="5" flood-color="#8f8ada" flood-opacity="0.55"/>
    </filter>
  </defs>
  <style>
    .title {{ font: 700 26px 'Microsoft YaHei', sans-serif; fill: #353535; }}
    .province-shadow {{ fill: #ffffff; stroke: #8f8ada; stroke-width: 4.8; filter: url(#outerGlow); }}
    .province-line {{ fill: #ffffff; stroke: #bdbdbd; stroke-width: 1.05; }}
    .province-label {{ font: 500 13px 'Microsoft YaHei', sans-serif; fill: #434343; text-anchor: middle; dominant-baseline: middle; }}
  </style>
  <rect width="{SVG_WIDTH}" height="{SVG_HEIGHT}" fill="#ffffff"/>
  <text class="title" x="54" y="54">中国地图</text>
  <g>
    {' '.join(province_paths)}
  </g>
  <g>
    {' '.join(province_labels)}
  </g>
</svg>
"""
    return svg, province_svgs, province_metadata


def main() -> None:
    geojson = fetch_geojson()
    svg, province_svgs, province_metadata = build_svg(geojson)
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(svg, encoding="utf-8")
    PROVINCE_OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for province_name, province_svg in province_svgs.items():
        slug = PROVINCE_SLUGS[province_name]
        (PROVINCE_OUTPUT_DIR / f"{slug}.svg").write_text(province_svg, encoding="utf-8")
    PROVINCE_METADATA_PATH.write_text(
        json.dumps(province_metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"Generated {OUTPUT_PATH}")
    print(f"Generated province assets in {PROVINCE_OUTPUT_DIR}")


if __name__ == "__main__":
    main()
