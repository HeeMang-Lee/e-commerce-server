package com.ecommerce.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.xerial.snappy.Snappy;

import java.io.IOException;

/**
 * Snappy 압축을 적용한 Redis Serializer
 *
 * 특징:
 * - Jackson2JsonRedisSerializer + Snappy 압축
 * - 타입 정보(@class)를 저장하지 않아 패키지 리팩토링에 안전
 * - 압축으로 Redis 메모리 사용량 절감 (평균 30~50% 압축률)
 *
 * 제네릭 타입 지원:
 * - TypeReference를 사용하여 List<ProductResponse> 같은 제네릭 타입 역직렬화 가능
 *
 * 참고: https://mangkyu.tistory.com/402
 */
public class SnappyRedisSerializer<T> implements RedisSerializer<T> {

    private final ObjectMapper objectMapper;
    private final TypeReference<T> typeReference;

    public SnappyRedisSerializer(ObjectMapper objectMapper, TypeReference<T> typeReference) {
        this.objectMapper = objectMapper;
        this.typeReference = typeReference;
    }

    @Override
    public byte[] serialize(T value) throws SerializationException {
        if (value == null) {
            return new byte[0];
        }

        try {
            // JSON 직렬화 후 Snappy 압축
            byte[] jsonBytes = objectMapper.writeValueAsBytes(value);
            return Snappy.compress(jsonBytes);
        } catch (IOException e) {
            throw new SerializationException("Could not serialize: " + e.getMessage(), e);
        }
    }

    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        try {
            // Snappy 압축 해제 후 JSON 역직렬화
            byte[] uncompressed = Snappy.uncompress(bytes);
            return objectMapper.readValue(uncompressed, typeReference);
        } catch (IOException e) {
            throw new SerializationException("Could not deserialize: " + e.getMessage(), e);
        }
    }
}

