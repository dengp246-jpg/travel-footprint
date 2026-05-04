package com.example.travelfootprint.service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ProvinceCatalogService {

    private static final List<ProvinceOption> PROVINCES = List.of(
            province("北京", "北京市", "北京"),
            province("天津", "天津市", "天津"),
            province("上海", "上海市", "上海"),
            province("重庆", "重庆市", "重庆"),
            province("河北", "河北省", "河北"),
            province("山西", "山西省", "山西"),
            province("辽宁", "辽宁省", "辽宁"),
            province("吉林", "吉林省", "吉林"),
            province("黑龙江", "黑龙江省", "黑龙江"),
            province("江苏", "江苏省", "江苏"),
            province("浙江", "浙江省", "浙江"),
            province("安徽", "安徽省", "安徽"),
            province("福建", "福建省", "福建"),
            province("江西", "江西省", "江西"),
            province("山东", "山东省", "山东"),
            province("河南", "河南省", "河南"),
            province("湖北", "湖北省", "湖北"),
            province("湖南", "湖南省", "湖南"),
            province("广东", "广东省", "广东"),
            province("海南", "海南省", "海南"),
            province("四川", "四川省", "四川"),
            province("贵州", "贵州省", "贵州"),
            province("云南", "云南省", "云南"),
            province("陕西", "陕西省", "陕西"),
            province("甘肃", "甘肃省", "甘肃"),
            province("青海", "青海省", "青海"),
            province("内蒙古", "内蒙古自治区", "内蒙古"),
            province("广西", "广西壮族自治区", "广西"),
            province("西藏", "西藏自治区", "西藏"),
            province("宁夏", "宁夏回族自治区", "宁夏"),
            province("新疆", "新疆维吾尔自治区", "新疆"),
            province("香港", "香港特别行政区", "香港"),
            province("澳门", "澳门特别行政区", "澳门"),
            province("台湾", "台湾省", "台湾"));

    public List<String> provinceNames() {
        return PROVINCES.stream()
                .map(ProvinceOption::name)
                .toList();
    }

    public Optional<String> normalizeProvince(String province) {
        if (province == null || province.isBlank()) {
            return Optional.empty();
        }
        return PROVINCES.stream()
                .filter(option -> Objects.equals(option.name(), province.trim()))
                .map(ProvinceOption::name)
                .findFirst();
    }

    public Optional<String> resolveProvince(String explicitProvince, String location) {
        Optional<String> normalizedProvince = normalizeProvince(explicitProvince);
        if (normalizedProvince.isPresent()) {
            return normalizedProvince;
        }
        if (location == null || location.isBlank()) {
            return Optional.empty();
        }
        String normalizedLocation = location.replace(" ", "");
        return PROVINCES.stream()
                .sorted(Comparator.comparingInt((ProvinceOption option) ->
                        option.aliases().stream().mapToInt(String::length).max().orElse(0)).reversed())
                .filter(option -> option.aliases().stream()
                        .map(alias -> alias.replace(" ", ""))
                        .anyMatch(normalizedLocation::contains))
                .map(ProvinceOption::name)
                .findFirst();
    }

    private static ProvinceOption province(String name, String... aliases) {
        return new ProvinceOption(name, List.of(aliases));
    }

    private record ProvinceOption(String name, List<String> aliases) {
    }
}
