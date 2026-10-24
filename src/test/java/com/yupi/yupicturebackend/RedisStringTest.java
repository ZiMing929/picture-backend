package com.yupi.yupicturebackend;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
public class RedisStringTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testRedisStringOperations() {

        // 1. 定义测试用的 Key 和 Value
        String key = "test:redis:string";
        String value = "testValue";

        // 2. 向 Redis 存储字符串
        stringRedisTemplate.opsForValue().set(key, value);

        // 3. 从 Redis 读取字符串
        String result = stringRedisTemplate.opsForValue().get(key);
        System.out.println("Redis 读取结果：" + result);

        // 4. 断言：读取的数据必须等于存储的数据
        Assertions.assertEquals(value, result, "Redis 存储的值与预期不一致");

        // 5. 修改 Redis 中的字符串
        stringRedisTemplate.opsForValue().set(key, "newValue");

        // 6. 验证修改后的值
        String newResult = stringRedisTemplate.opsForValue().get(key);
        Assertions.assertEquals("newValue", newResult, "Redis 修改数据失败");

        // 7. 删除测试数据
        Boolean deleted = stringRedisTemplate.delete(key);
        Assertions.assertTrue(Boolean.TRUE.equals(deleted), "Redis 删除数据失败");

        // 8. 验证删除成功
        Assertions.assertNull(stringRedisTemplate.opsForValue().get(key));

        System.out.println("Redis 字符串增删改查测试全部通过！");
    }
}
