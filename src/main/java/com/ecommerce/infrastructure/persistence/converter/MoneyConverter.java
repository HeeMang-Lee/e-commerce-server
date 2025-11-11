package com.ecommerce.infrastructure.persistence.converter;

import com.ecommerce.domain.vo.Money;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Money money) {
        return money == null ? null : money.getAmount();
    }

    @Override
    public Money convertToEntityAttribute(Integer amount) {
        return amount == null ? null : Money.of(amount);
    }
}
