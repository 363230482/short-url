package com.zb.shorturl.service.impl;

import com.zb.shorturl.entity.ShortUrl;
import com.zb.shorturl.mapper.ShortUrlMapper;
import com.zb.shorturl.service.ShortUrlNumService;
import com.zb.shorturl.service.ShortUrlService;
import com.zb.shorturl.utils.ShortUrlUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * @author zhangbo
 * @date 2020-01-19
 */
@Service
public class ShortUrlServiceImpl implements ShortUrlService {

    @Value("${short-url.cache-minutes:300}")
    private long cacheMinutes;

    @Value("${short-url.server-path}")
    private String serverPath;

    @Autowired
    private ShortUrlMapper shortUrlMapper;

    @Autowired
    private ShortUrlNumService shortUrlNumService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public String getShortUrl(String longUrl, String appKey) {
        String shortUrl = ShortUrlUtil.shortUrl(shortUrlNumService.getCurrentNum());
        shortUrlMapper.insert(buildEntity(shortUrl, longUrl, appKey));
        stringRedisTemplate.boundValueOps(getKey(shortUrl)).set(longUrl, Duration.ofMinutes(cacheMinutes));
        return serverPath + shortUrl;
    }

    private String getKey(String shortUrl) {
        return ShortUrlUtil.SHORT_URL_PRE + shortUrl;
    }

    private ShortUrl buildEntity(String shortUrl, String longUrl, String appKey) {
        ShortUrl entity = new ShortUrl();
        entity.setShortUrl(shortUrl);
        entity.setLongUrl(longUrl);
        entity.setAppKey(appKey);
        entity.setCreateTime(LocalDateTime.now());
        return entity;
    }

    @Override
    public String getLongUrl(String shortUrl) {
        String key = getKey(shortUrl);
        String longUrl = stringRedisTemplate.boundValueOps(key).get();
        if (StringUtils.isEmpty(longUrl)) {
            longUrl = shortUrlMapper.selectLongUrl(shortUrl);
            if (StringUtils.isEmpty(longUrl)) {
                // 为空时，保护性缓存10秒
                stringRedisTemplate.boundValueOps(key).set(ShortUrlUtil.EMPTY_SHORT_URL, Duration.ofSeconds(10));
            } else {
                stringRedisTemplate.boundValueOps(key).set(longUrl, Duration.ofMinutes(cacheMinutes));
            }
        }
        return longUrl;
    }
}
