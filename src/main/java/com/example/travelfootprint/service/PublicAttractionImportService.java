package com.example.travelfootprint.service;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PublicAttractionImportService {

    private static final TypeReference<List<BaiduScenicSeed>> BAIDU_SEED_LIST = new TypeReference<>() {
    };

    private final TravelPostRepository postRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ContentVisibilityService contentVisibilityService;
    private final LocationNormalizationService locationNormalizationService;
    private final ObjectMapper objectMapper;
    private final Path scenicSeedFile;

    public PublicAttractionImportService(
            TravelPostRepository postRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ContentVisibilityService contentVisibilityService,
            LocationNormalizationService locationNormalizationService,
            ObjectMapper objectMapper,
            @Value("${app.import.baidu-scenic-file:data/baidu-scenic-seeds.json}") String scenicSeedFileLocation) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.contentVisibilityService = contentVisibilityService;
        this.locationNormalizationService = locationNormalizationService;
        this.objectMapper = objectMapper;
        this.scenicSeedFile = Paths.get(scenicSeedFileLocation).toAbsolutePath().normalize();
    }

    public ImportResult importBaiduScenicDescriptions() {
        User sourceUser = findOrCreateSourceUser(
                "baidubot",
                "旅迹百度景点助手",
                "负责整理百度来源的景点描述型资料，补充适合展示在旅游系统里的景点卡片。");
        int imported = 0;
        int skipped = 0;

        for (BaiduScenicSeed seed : loadBaiduScenicSeeds()) {
            if (postRepository.existsBySourceUrl(seed.sourceUrl())) {
                skipped++;
                continue;
            }

            TravelPost post = new TravelPost();
            post.setAuthor(sourceUser);
            post.setTitle(seed.title());
            post.setLocation(locationNormalizationService.normalizeDisplayLocation(seed.province(), seed.location()));
            post.setProvince(seed.province());
            post.setCategory("景点资料");
            post.setTags(orEmpty(seed.tags()));
            post.setTravelDate(LocalDate.now().minusDays(resolveDayOffset(seed.dayOffset())));
            post.setSourceName(seed.sourceName().isBlank() ? "百度百科" : seed.sourceName());
            post.setSourceUrl(seed.sourceUrl());
            post.setContent(buildBaiduDescription(seed));
            post.setReviewStatus(contentVisibilityService.defaultPostStatus(sourceUser, true));
            postRepository.save(post);
            imported++;
        }

        return new ImportResult(imported, skipped);
    }

    private String buildBaiduDescription(BaiduScenicSeed seed) {
        return seed.description()
                + "\n\n"
                + "推荐玩法："
                + orEmpty(seed.playSuggestion())
                + "。\n"
                + "适合人群："
                + orEmpty(seed.audience())
                + "。\n"
                + "资料说明：本条内容由系统根据可维护的景点资料文件整理为景点描述卡，可用于首页展示、地图联动和行程规划参考。\n\n"
                + "来源链接："
                + seed.sourceUrl();
    }

    private List<BaiduScenicSeed> loadBaiduScenicSeeds() {
        try {
            if (Files.notExists(scenicSeedFile)) {
                List<BaiduScenicSeed> defaults = defaultBaiduScenicSeeds();
                writeSeedFile(defaults);
                return defaults;
            }

            List<BaiduScenicSeed> loaded = objectMapper.readValue(scenicSeedFile.toFile(), BAIDU_SEED_LIST);
            if (loaded == null || loaded.isEmpty()) {
                return List.of();
            }
            return normalizeSeeds(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "景点资料文件读取失败，请检查 " + scenicSeedFile + " 是否存在且为有效 JSON 格式。",
                    exception);
        }
    }

    private void writeSeedFile(List<BaiduScenicSeed> seeds) throws IOException {
        Path parent = scenicSeedFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(scenicSeedFile.toFile(), seeds);
    }

    private List<BaiduScenicSeed> normalizeSeeds(List<BaiduScenicSeed> loaded) {
        Map<String, BaiduScenicSeed> unique = new LinkedHashMap<>();
        for (int index = 0; index < loaded.size(); index++) {
            BaiduScenicSeed seed = loaded.get(index);
            if (seed == null) {
                throw new IllegalStateException("景点资料文件第 " + (index + 1) + " 条记录为空。");
            }
            requireValue(seed.title(), "title", index);
            requireValue(seed.province(), "province", index);
            requireValue(seed.location(), "location", index);
            requireValue(seed.description(), "description", index);
            requireValue(seed.sourceUrl(), "sourceUrl", index);

            BaiduScenicSeed normalized = new BaiduScenicSeed(
                    seed.title().trim(),
                    seed.province().trim(),
                    seed.location().trim(),
                    seed.description().trim(),
                    orEmpty(seed.playSuggestion()).trim(),
                    orEmpty(seed.audience()).trim(),
                    orEmpty(seed.tags()).trim(),
                    seed.sourceUrl().trim(),
                    orEmpty(seed.sourceName()).trim(),
                    seed.dayOffset());
            unique.putIfAbsent(normalized.sourceUrl(), normalized);
        }
        return List.copyOf(unique.values());
    }

    private void requireValue(String value, String fieldName, int index) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("景点资料文件第 " + (index + 1) + " 条缺少字段 " + fieldName + "。");
        }
    }

    private long resolveDayOffset(Integer dayOffset) {
        if (dayOffset == null || dayOffset < 0) {
            return 0;
        }
        return dayOffset;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private User findOrCreateSourceUser(String username, String nickname, String bio) {
        return userRepository.findByUsername(username)
                .map(existing -> updateSourceUser(existing, nickname, bio))
                .orElseGet(() -> createSourceUser(username, nickname, bio));
    }

    private User updateSourceUser(User user, String nickname, String bio) {
        boolean changed = false;
        if (!nickname.equals(user.getNickname())) {
            user.setNickname(nickname);
            changed = true;
        }
        if (!bio.equals(user.getBio())) {
            user.setBio(bio);
            changed = true;
        }
        if (!user.isEnabled()) {
            user.setEnabled(true);
            changed = true;
        }
        return changed ? userRepository.save(user) : user;
    }

    private User createSourceUser(String username, String nickname, String bio) {
        User user = new User();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setPasswordHash(passwordEncoder.encode("123456"));
        user.setBio(bio);
        user.setEnabled(true);
        user.setAdmin(false);
        return userRepository.save(user);
    }

    private List<BaiduScenicSeed> defaultBaiduScenicSeeds() {
        return List.of(
                new BaiduScenicSeed(
                        "西湖",
                        "浙江",
                        "浙江 杭州 西湖",
                        "西湖以湖山相映的城市风景著称，苏堤、白堤、断桥和雷峰塔共同构成了杭州最具代表性的湖滨景观，四季景色变化明显，适合慢慢逛、边走边看。",
                        "环湖步行、游船看湖心三岛，再把灵隐、岳庙或河坊街安排成一日或两日联游",
                        "第一次到杭州、喜欢城市风景与轻松漫游的人",
                        "西湖,杭州,湖景,城市漫游,经典景点",
                        "https://baike.baidu.com/search/word?word=西湖风景名胜区",
                        "百度百科",
                        2),
                new BaiduScenicSeed(
                        "鼓浪屿",
                        "福建",
                        "福建 厦门 鼓浪屿",
                        "鼓浪屿兼具海岛气质与近代建筑风貌，小岛不大但街巷很密，钢琴文化、万国建筑和海边步道让它很适合用散步的方式去体验。",
                        "白天逛街巷和建筑群，傍晚看海岸线和日落，再搭配厦门本岛的中山路或沙坡尾",
                        "喜欢拍照、海岛散步和历史建筑的人",
                        "鼓浪屿,厦门,海岛,建筑,文艺漫游",
                        "https://baike.baidu.com/search/word?word=鼓浪屿",
                        "百度百科",
                        4),
                new BaiduScenicSeed(
                        "黄山风景区",
                        "安徽",
                        "安徽 黄山 黄山风景区",
                        "黄山以奇松、怪石、云海和冬雪闻名，山岳景观层次感很强，日出和云海是很多游客专门上山等待的重点体验。",
                        "根据体力选择前山或后山线路，住一晚山上更容易看日出，也方便把西海大峡谷和光明顶串起来",
                        "喜欢登山、摄影和山岳风光的人",
                        "黄山,山岳,云海,日出,徒步",
                        "https://baike.baidu.com/search/word?word=黄山风景名胜区",
                        "百度百科",
                        6),
                new BaiduScenicSeed(
                        "九寨沟",
                        "四川",
                        "四川 阿坝 九寨沟",
                        "九寨沟的核心吸引力在于高原湖泊、彩林、瀑布和层次分明的沟谷景观，水色变化丰富，秋季和晴天时的视觉效果尤其突出。",
                        "优先安排长沟深度游，尽量早入园，把镜海、诺日朗和长海一线留出足够时间",
                        "适合自然风光爱好者和摄影人群",
                        "九寨沟,高原湖泊,彩林,瀑布,摄影",
                        "https://baike.baidu.com/search/word?word=九寨沟风景名胜区",
                        "百度百科",
                        8),
                new BaiduScenicSeed(
                        "故宫博物院",
                        "北京",
                        "北京 故宫博物院",
                        "故宫兼具皇家宫殿建筑群与大型博物馆双重属性，中轴线空间秩序鲜明，院落和殿宇规模宏大，是理解北京历史与古代宫廷文化的核心景点之一。",
                        "提前看展览和开放区域，主线走中轴线，感兴趣的话再补珍宝馆或钟表馆",
                        "适合历史、建筑、文博类游客",
                        "故宫,北京,宫殿,博物馆,中轴线",
                        "https://baike.baidu.com/search/word?word=故宫博物院",
                        "百度百科",
                        10),
                new BaiduScenicSeed(
                        "布达拉宫",
                        "西藏",
                        "西藏 拉萨 布达拉宫",
                        "布达拉宫是拉萨最具辨识度的高原地标，建筑依山而起，视觉冲击力很强，同时承载着浓厚的历史与宗教文化意义。",
                        "把参观节奏放慢，先适应高原，再结合大昭寺和八廓街安排拉萨核心一日游",
                        "适合对高原风光、历史建筑和藏地文化感兴趣的人",
                        "布达拉宫,拉萨,高原,宫殿,藏地文化",
                        "https://baike.baidu.com/search/word?word=布达拉宫",
                        "百度百科",
                        12),
                new BaiduScenicSeed(
                        "丽江古城",
                        "云南",
                        "云南 丽江 丽江古城",
                        "丽江古城的吸引力在于高原古城街巷、水系、木构建筑和夜晚氛围感，白天适合慢逛拍照，晚上则更热闹，也更有旅行社交感。",
                        "白天看街巷和水系，晚上体验古城夜景，再把玉龙雪山或束河古镇作为联动行程",
                        "适合轻松度假、情侣游和朋友结伴游",
                        "丽江古城,云南,古城,夜景,高原慢游",
                        "https://baike.baidu.com/search/word?word=丽江古城",
                        "百度百科",
                        14),
                new BaiduScenicSeed(
                        "龙门石窟",
                        "河南",
                        "河南 洛阳 龙门石窟",
                        "龙门石窟是洛阳极具代表性的石刻艺术景区，造像数量多、时代跨度长，伊河两岸的崖壁景观与石窟艺术结合得非常紧密。",
                        "建议按西山石窟主线参观，再结合香山寺和白园做半日到一日安排",
                        "适合历史、雕塑艺术和研学游客",
                        "龙门石窟,洛阳,石窟,雕塑,历史文化",
                        "https://baike.baidu.com/search/word?word=龙门石窟",
                        "百度百科",
                        16),
                new BaiduScenicSeed(
                        "乐山大佛",
                        "四川",
                        "四川 乐山 乐山大佛",
                        "乐山大佛临江而立，体量感非常强，从山上近看与乘船远看会得到两种不同的观看体验，是四川非常典型的人文景观之一。",
                        "优先安排陆路近距离看佛像细节，如果时间够，再补一段江上视角",
                        "适合家庭游客、人文旅行者和首次到川南的游客",
                        "乐山大佛,四川,大佛,江景,人文景观",
                        "https://baike.baidu.com/search/word?word=乐山大佛",
                        "百度百科",
                        18),
                new BaiduScenicSeed(
                        "武夷山",
                        "福建",
                        "福建 南平 武夷山",
                        "武夷山把丹霞地貌、溪流竹筏、茶文化和山林步道结合在一起，适合一边看山水，一边体验福建北部的茶旅氛围。",
                        "把九曲溪竹筏和天游峰作为主线，再留时间给茶园与岩茶体验",
                        "适合山水游、亲子游和喜欢喝茶的人",
                        "武夷山,福建,丹霞,竹筏,茶文化",
                        "https://baike.baidu.com/search/word?word=武夷山风景名胜区",
                        "百度百科",
                        20),
                new BaiduScenicSeed(
                        "秦始皇兵马俑",
                        "陕西",
                        "陕西 西安 秦始皇兵马俑",
                        "兵马俑最具吸引力的地方在于规模感与考古现场感并存，不同坑位和陶俑细节能让人直观感受到秦代军事与工艺水平。",
                        "建议和华清宫或西安城内博物馆联游，提前了解一二三号坑的参观重点会更有代入感",
                        "适合历史控、亲子研学和第一次到西安的人",
                        "兵马俑,西安,考古,博物馆,历史",
                        "https://baike.baidu.com/search/word?word=秦始皇兵马俑博物馆",
                        "百度百科",
                        22),
                new BaiduScenicSeed(
                        "张家界国家森林公园",
                        "湖南",
                        "湖南 张家界 国家森林公园",
                        "张家界国家森林公园以石峰林立、峡谷纵深和高空观景体验著称，垂直地貌非常有辨识度，适合把索道、步行和观景平台结合起来玩。",
                        "合理安排天子山、袁家界和金鞭溪等区域，尽量避开中午高峰时段",
                        "适合喜欢山地地貌、拍照和户外步行的人",
                        "张家界,森林公园,石峰,峡谷,户外",
                        "https://baike.baidu.com/search/word?word=张家界国家森林公园",
                        "百度百科",
                        24));
    }

    public record ImportResult(int importedCount, int skippedCount) {
    }

    private record BaiduScenicSeed(
            String title,
            String province,
            String location,
            String description,
            String playSuggestion,
            String audience,
            String tags,
            String sourceUrl,
            String sourceName,
            Integer dayOffset) {
    }
}
