package com.example.travelfootprint.service;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AppCatalogService {

    public List<String> categories() {
        return List.of("自然风光", "海岛滨海", "城市漫游", "古镇人文", "美食探店", "徒步露营", "摄影打卡");
    }
}
