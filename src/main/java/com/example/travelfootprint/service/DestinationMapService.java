package com.example.travelfootprint.service;

import com.example.travelfootprint.model.TravelPost;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DestinationMapService {

    private static final double SVG_WIDTH = 1600.0;
    private static final double SVG_HEIGHT = 1200.0;
    private static final double CHINA_MIN_LONGITUDE = 73.5;
    private static final double CHINA_MAX_LONGITUDE = 134.8;
    private static final double CHINA_MIN_LATITUDE = 18.0;
    private static final double CHINA_MAX_LATITUDE = 53.8;
    private static final double SVG_MARGIN_LEFT = 70.0;
    private static final double SVG_MARGIN_RIGHT = 70.0;
    private static final double SVG_MARGIN_TOP = 80.0;
    private static final double SVG_MARGIN_BOTTOM = 100.0;
    private static final double MAP_SCALE = Math.min(
            (SVG_WIDTH - SVG_MARGIN_LEFT - SVG_MARGIN_RIGHT) / (CHINA_MAX_LONGITUDE - CHINA_MIN_LONGITUDE),
            (SVG_HEIGHT - SVG_MARGIN_TOP - SVG_MARGIN_BOTTOM) / (CHINA_MAX_LATITUDE - CHINA_MIN_LATITUDE));
    private static final double MAP_ORIGIN_X = SVG_MARGIN_LEFT
            + ((SVG_WIDTH - SVG_MARGIN_LEFT - SVG_MARGIN_RIGHT)
            - (CHINA_MAX_LONGITUDE - CHINA_MIN_LONGITUDE) * MAP_SCALE) / 2.0;
    private static final double MAP_ORIGIN_Y = SVG_MARGIN_TOP
            + ((SVG_HEIGHT - SVG_MARGIN_TOP - SVG_MARGIN_BOTTOM)
            - (CHINA_MAX_LATITUDE - CHINA_MIN_LATITUDE) * MAP_SCALE) / 2.0;

    private static final List<DestinationAnchor> DESTINATION_ANCHORS = List.of(
            destination("浙江", 120.1551, 30.2741, "西湖", "杭州", "杭州西湖"),
            destination("福建", 118.0667, 24.4486, "鼓浪屿", "厦门", "厦门鼓浪屿"),
            destination("湖南", 109.5996, 27.9483, "凤凰古城", "凤凰", "湘西"),
            destination("安徽", 118.3376, 29.7147, "黄山", "黄山风景区"),
            destination("四川", 103.9187, 33.2520, "九寨沟"),
            destination("四川", 100.2980, 28.4861, "稻城亚丁", "亚丁", "稻城"),
            destination("陕西", 109.2732, 34.3841, "兵马俑", "秦始皇兵马俑"),
            destination("北京", 116.4039, 39.9240, "故宫博物院", "故宫"),
            destination("西藏", 91.1175, 29.6570, "布达拉宫", "拉萨"),
            destination("四川", 103.4920, 29.5449, "峨眉山"),
            destination("河南", 112.4777, 34.5607, "龙门石窟", "洛阳"),
            destination("四川", 103.7730, 29.5442, "乐山大佛", "乐山"),
            destination("湖南", 112.9454, 28.1894, "岳麓山"),
            destination("湖南", 110.4792, 29.3167, "武陵源", "张家界", "国家森林公园", "张家界国家森林公园"),
            destination("云南", 100.2340, 26.8721, "丽江古城", "丽江"),
            destination("江苏", 120.6196, 31.2990, "苏州园林", "苏州"),
            destination("福建", 117.9910, 27.7510, "武夷山", "南平"),
            destination("上海", 121.4905, 31.2417, "外滩", "黄浦", "上海"),
            destination("山东", 120.3826, 36.0671, "栈桥", "青岛"),
            destination("山东", 120.4470, 36.0662, "八大关"),
            destination("山东", 120.5050, 36.0350, "小麦岛"),
            destination("上海", 121.4737, 31.2304, "上海"),
            destination("江苏", 118.7969, 32.0603, "南京"),
            destination("湖北", 114.3055, 30.5928, "武汉"),
            destination("湖南", 112.9388, 28.2282, "长沙"),
            destination("四川", 104.0665, 30.5728, "成都"),
            destination("重庆", 106.5516, 29.5630, "重庆"),
            destination("陕西", 108.9398, 34.3416, "西安"),
            destination("天津", 117.2000, 39.1333, "天津"),
            destination("北京", 116.4074, 39.9042, "北京"),
            destination("江苏", 120.5853, 31.2989, "苏州"),
            destination("安徽", 117.2830, 31.8612, "合肥"),
            destination("福建", 119.2965, 26.0745, "福州"),
            destination("广东", 113.2644, 23.1291, "广州"),
            destination("广东", 114.0579, 22.5431, "深圳"),
            destination("云南", 102.8329, 24.8801, "昆明"),
            destination("河南", 113.6254, 34.7466, "郑州"),
            destination("贵州", 106.6302, 26.6470, "贵阳"),
            destination("西藏", 91.1322, 29.6604, "西藏"),
            destination("新疆", 87.6168, 43.8256, "乌鲁木齐", "新疆"),
            destination("内蒙古", 111.6708, 40.8183, "呼和浩特", "内蒙古"),
            destination("辽宁", 123.4291, 41.7968, "沈阳"),
            destination("吉林", 125.3235, 43.8171, "长春"),
            destination("黑龙江", 126.5349, 45.8038, "哈尔滨"),
            destination("山东", 117.1205, 36.6512, "济南"),
            destination("湖南", 113.0000, 28.2000, "湖南"),
            destination("湖南", 109.7392, 28.3119, "湘西"),
            destination("浙江", 119.3000, 29.1000, "浙江"),
            destination("福建", 117.0000, 26.1000, "福建"),
            destination("江西", 115.8582, 28.6829, "江西"),
            destination("四川", 103.8000, 30.9000, "四川"),
            destination("陕西", 108.9000, 34.2000, "陕西"),
            destination("青海", 101.7800, 36.6200, "青海"),
            destination("云南", 102.7000, 25.0000, "云南"),
            destination("西藏", 91.0000, 31.5000, "拉萨"));

    private static final Map<String, GeoAnchor> CITY_CLUSTER_ANCHORS = Map.ofEntries(
            Map.entry("杭州", city("浙江", 120.1551, 30.2741)),
            Map.entry("厦门", city("福建", 118.0894, 24.4798)),
            Map.entry("长沙", city("湖南", 112.9388, 28.2282)),
            Map.entry("青岛", city("山东", 120.3826, 36.0671)),
            Map.entry("北京", city("北京", 116.4074, 39.9042)),
            Map.entry("上海", city("上海", 121.4737, 31.2304)),
            Map.entry("苏州", city("江苏", 120.5853, 31.2989)),
            Map.entry("洛阳", city("河南", 112.4777, 34.5607)),
            Map.entry("乐山", city("四川", 103.7730, 29.5442)),
            Map.entry("南平", city("福建", 117.9910, 27.7510)),
            Map.entry("丽江", city("云南", 100.2340, 26.8721)),
            Map.entry("拉萨", city("西藏", 91.1175, 29.6570)),
            Map.entry("西安", city("陕西", 108.9398, 34.3416)),
            Map.entry("成都", city("四川", 104.0665, 30.5728)),
            Map.entry("重庆", city("重庆", 106.5516, 29.5630)),
            Map.entry("武汉", city("湖北", 114.3055, 30.5928)),
            Map.entry("南京", city("江苏", 118.7969, 32.0603)),
            Map.entry("广州", city("广东", 113.2644, 23.1291)),
            Map.entry("深圳", city("广东", 114.0579, 22.5431)),
            Map.entry("昆明", city("云南", 102.8329, 24.8801)),
            Map.entry("郑州", city("河南", 113.6254, 34.7466)),
            Map.entry("贵阳", city("贵州", 106.6302, 26.6470)),
            Map.entry("福州", city("福建", 119.2965, 26.0745)),
            Map.entry("合肥", city("安徽", 117.2830, 31.8612)),
            Map.entry("天津", city("天津", 117.2000, 39.1333)),
            Map.entry("沈阳", city("辽宁", 123.4291, 41.7968)),
            Map.entry("长春", city("吉林", 125.3235, 43.8171)),
            Map.entry("哈尔滨", city("黑龙江", 126.5349, 45.8038)));

    private static final List<ProvinceAnchor> PROVINCE_FALLBACKS = List.of(
            province("北京", 116.4074, 39.9042),
            province("天津", 117.2000, 39.1333),
            province("河北", 114.5149, 38.0428),
            province("山西", 112.5492, 37.8570),
            province("内蒙古", 111.7492, 40.8426),
            province("辽宁", 123.4291, 41.7968),
            province("吉林", 125.3235, 43.8171),
            province("黑龙江", 126.5349, 45.8038),
            province("上海", 121.4737, 31.2304),
            province("江苏", 118.7969, 32.0603),
            province("浙江", 120.1528, 30.2674),
            province("安徽", 117.2272, 31.8206),
            province("福建", 119.2965, 26.0745),
            province("江西", 115.8582, 28.6829),
            province("山东", 117.1201, 36.6512),
            province("河南", 113.6254, 34.7466),
            province("湖北", 114.3055, 30.5928),
            province("湖南", 112.9388, 28.2282),
            province("广东", 113.2644, 23.1291),
            province("广西", 108.3200, 22.8240),
            province("海南", 110.3492, 20.0174),
            province("重庆", 106.5516, 29.5630),
            province("四川", 104.0665, 30.5728),
            province("贵州", 106.7074, 26.5982),
            province("云南", 102.8329, 24.8801),
            province("西藏", 91.1322, 29.6604),
            province("陕西", 108.9398, 34.3416),
            province("甘肃", 103.8343, 36.0611),
            province("青海", 101.7782, 36.6171),
            province("宁夏", 106.2782, 38.4664),
            province("新疆", 87.6168, 43.8256),
            province("台湾", 121.5654, 25.0330),
            province("香港", 114.1694, 22.3193),
            province("澳门", 113.5439, 22.1987));

    private final ProvinceCatalogService provinceCatalogService;
    private final LocationNormalizationService locationNormalizationService;

    public DestinationMapService(
            ProvinceCatalogService provinceCatalogService,
            LocationNormalizationService locationNormalizationService) {
        this.provinceCatalogService = provinceCatalogService;
        this.locationNormalizationService = locationNormalizationService;
    }

    public List<LocationSuggestion> locationSuggestions() {
        Map<String, LocationSuggestion> suggestions = new LinkedHashMap<>();
        for (DestinationAnchor anchor : DESTINATION_ANCHORS) {
            if (anchor.keywords().isEmpty()) {
                continue;
            }
            String landmark = anchor.keywords().get(0);
            if (landmark.equals(anchor.province())) {
                continue;
            }
            String cityName = anchor.keywords().stream()
                    .skip(1)
                    .filter(CITY_CLUSTER_ANCHORS::containsKey)
                    .findFirst()
                    .orElse("");
            String locationValue = cityName.isBlank() || landmark.contains(cityName)
                    ? landmark
                    : cityName + " " + landmark;
            MapPoint point = project(anchor.longitude(), anchor.latitude());
            LocationSuggestion suggestion = new LocationSuggestion(
                    locationValue,
                    anchor.province(),
                    anchor.longitude(),
                    anchor.latitude(),
                    point.left(),
                    point.top(),
                    (anchor.province() + " " + String.join(" ", anchor.keywords())).toLowerCase());
            suggestions.putIfAbsent(anchor.province() + "|" + locationValue, suggestion);
        }
        CITY_CLUSTER_ANCHORS.forEach((cityName, anchor) -> {
            MapPoint point = project(anchor.longitude(), anchor.latitude());
            LocationSuggestion suggestion = new LocationSuggestion(
                    cityName,
                    anchor.province(),
                    anchor.longitude(),
                    anchor.latitude(),
                    point.left(),
                    point.top(),
                    (anchor.province() + " " + cityName).toLowerCase());
            suggestions.putIfAbsent(anchor.province() + "|" + cityName, suggestion);
        });
        return List.copyOf(suggestions.values());
    }

    public ArrivalMatch matchArrival(double longitude, double latitude) {
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("定位坐标超出有效范围。");
        }
        LocationSuggestion nearest = locationSuggestions().stream()
                .min(Comparator.comparingDouble(suggestion -> distanceKilometers(
                        latitude, longitude, suggestion.latitude(), suggestion.longitude())))
                .orElse(null);
        if (nearest == null) {
            return new ArrivalMatch("当前位置", "", false, 0);
        }
        double distance = distanceKilometers(
                latitude, longitude, nearest.latitude(), nearest.longitude());
        boolean matched = distance <= 30.0;
        return new ArrivalMatch(
                matched ? nearest.location() : "当前位置",
                matched ? nearest.province() : "",
                matched,
                Math.round(distance * 10.0) / 10.0);
    }

    public Optional<MapPoint> resolvePoint(TravelPost post) {
        return resolvePlacement(post).map(MapPlacement::point);
    }

    /**
     * Resolves the geographic coordinate represented by the same local anchors used by the web map.
     * This keeps native clients aligned with the web visualization without calling an external map API.
     */
    public Optional<GeoPoint> resolveCoordinates(TravelPost post) {
        if (hasValidCoordinates(post)) {
            return Optional.of(new GeoPoint(post.getLongitude(), post.getLatitude()));
        }
        return resolvePlacement(post).map(placement -> unproject(placement.point()));
    }

    public Optional<MapPlacement> resolvePlacement(TravelPost post) {
        String primaryLocation = locationNormalizationService.primaryLocationSegment(post);
        String searchText = ((post.getProvince() == null ? "" : post.getProvince()) + " " + primaryLocation)
                .replace(" ", "");
        String placementLabel = primaryLocation.isBlank()
                ? locationNormalizationService.normalizeDisplayLocation(post)
                : primaryLocation;
        Optional<String> province = provinceCatalogService.resolveProvince(post.getProvince(), post.getLocation());

        if (hasValidCoordinates(post)) {
            return Optional.of(new MapPlacement(
                    project(post.getLongitude(), post.getLatitude()),
                    placementLabel,
                    placementLabel));
        }

        Optional<String> clusterLabel = resolveClusterLabel(post, primaryLocation);
        Optional<GeoAnchor> clusterAnchor = clusterLabel
                .map(CITY_CLUSTER_ANCHORS::get)
                .filter(anchor -> province.isEmpty() || province.get().equals(anchor.province()));
        Optional<DestinationAnchor> preciseAnchor = DESTINATION_ANCHORS.stream()
                .sorted(Comparator.comparingInt((DestinationAnchor anchor) -> anchor.maxKeywordLength()).reversed())
                .filter(anchor -> anchor.matches(searchText))
                .filter(anchor -> province.isEmpty() || province.get().equals(anchor.province()))
                .findFirst();
        if (clusterAnchor.isPresent()) {
            return Optional.of(new MapPlacement(project(clusterAnchor.get().longitude(), clusterAnchor.get().latitude()),
                    clusterLabel.orElse(placementLabel), clusterLabel.orElse(placementLabel)));
        }
        if (preciseAnchor.isPresent()) {
            return Optional.of(new MapPlacement(
                    project(preciseAnchor.get().longitude(), preciseAnchor.get().latitude()),
                    placementLabel,
                    placementLabel));
        }

        if (province.isEmpty()) {
            return Optional.empty();
        }

        Optional<ProvinceAnchor> fallbackAnchor = PROVINCE_FALLBACKS.stream()
                .filter(anchor -> anchor.name().equals(province.get()))
                .findFirst();
        if (fallbackAnchor.isEmpty()) {
            return Optional.empty();
        }

        String seedText = ((post.getProvince() == null ? "" : post.getProvince())
                + "|"
                + placementLabel).replace(" ", "");
        int hash = Math.abs(seedText.hashCode());
        double jitterLongitude = ((hash % 5) - 2) * 0.18;
        double jitterLatitude = (((hash / 5) % 5) - 2) * 0.14;

        return Optional.of(new MapPlacement(
                project(fallbackAnchor.get().longitude() + jitterLongitude,
                        fallbackAnchor.get().latitude() + jitterLatitude),
                placementLabel,
                placementLabel));
    }

    private MapPoint project(double longitude, double latitude) {
        double x = MAP_ORIGIN_X + (longitude - CHINA_MIN_LONGITUDE) * MAP_SCALE;
        double y = MAP_ORIGIN_Y + (CHINA_MAX_LATITUDE - latitude) * MAP_SCALE;
        return new MapPoint(
                roundPercent(clamp(x / SVG_WIDTH * 100.0, 4.0, 96.0)),
                roundPercent(clamp(y / SVG_HEIGHT * 100.0, 4.0, 96.0)));
    }

    private GeoPoint unproject(MapPoint point) {
        double x = point.left() / 100.0 * SVG_WIDTH;
        double y = point.top() / 100.0 * SVG_HEIGHT;
        double longitude = (x - MAP_ORIGIN_X) / MAP_SCALE + CHINA_MIN_LONGITUDE;
        double latitude = CHINA_MAX_LATITUDE - (y - MAP_ORIGIN_Y) / MAP_SCALE;
        return new GeoPoint(
                Math.round(longitude * 1000000.0) / 1000000.0,
                Math.round(latitude * 1000000.0) / 1000000.0);
    }

    private boolean hasValidCoordinates(TravelPost post) {
        if (post.getLongitude() == null || post.getLatitude() == null) {
            return false;
        }
        return post.getLongitude() >= CHINA_MIN_LONGITUDE
                && post.getLongitude() <= CHINA_MAX_LONGITUDE
                && post.getLatitude() >= CHINA_MIN_LATITUDE
                && post.getLatitude() <= CHINA_MAX_LATITUDE;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double roundPercent(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double distanceKilometers(
            double firstLatitude,
            double firstLongitude,
            double secondLatitude,
            double secondLongitude) {
        double latitudeDelta = Math.toRadians(secondLatitude - firstLatitude);
        double longitudeDelta = Math.toRadians(secondLongitude - firstLongitude);
        double firstLatitudeRadians = Math.toRadians(firstLatitude);
        double secondLatitudeRadians = Math.toRadians(secondLatitude);
        double haversine = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(firstLatitudeRadians) * Math.cos(secondLatitudeRadians)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }

    private static DestinationAnchor destination(
            String province,
            double longitude,
            double latitude,
            String... keywords) {
        return new DestinationAnchor(province, longitude, latitude, List.of(keywords));
    }

    private static GeoAnchor city(String province, double longitude, double latitude) {
        return new GeoAnchor(province, longitude, latitude);
    }

    private static ProvinceAnchor province(String name, double longitude, double latitude) {
        return new ProvinceAnchor(name, longitude, latitude);
    }

    private Optional<String> resolveClusterLabel(TravelPost post, String primaryLocation) {
        if (primaryLocation == null || primaryLocation.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(primaryLocation)
                .filter(CITY_CLUSTER_ANCHORS::containsKey);
    }

    private record DestinationAnchor(String province, double longitude, double latitude, List<String> keywords) {

        private boolean matches(String normalizedText) {
            return keywords.stream()
                    .map(keyword -> keyword.replace(" ", ""))
                    .anyMatch(normalizedText::contains);
        }

        private int maxKeywordLength() {
            return keywords.stream()
                    .mapToInt(String::length)
                    .max()
                    .orElse(0);
        }
    }

    private record ProvinceAnchor(String name, double longitude, double latitude) {
    }

    private record GeoAnchor(String province, double longitude, double latitude) {
    }

    public record MapPlacement(MapPoint point, String groupKey, String groupLabel) {
    }

    public record MapPoint(double left, double top) {
    }

    public record GeoPoint(double longitude, double latitude) {
    }

    public record LocationSuggestion(
            String location,
            String province,
            double longitude,
            double latitude,
            double left,
            double top,
            String searchText) {
    }

    public record ArrivalMatch(
            String location,
            String province,
            boolean matched,
            double distanceKilometers) {
    }
}
