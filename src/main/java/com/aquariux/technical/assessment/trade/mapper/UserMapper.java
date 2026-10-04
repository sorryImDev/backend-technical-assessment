package com.aquariux.technical.assessment.trade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT id FROM users WHERE id = #{userId} FOR UPDATE
            """)
    Long lockById(Long userId);
}
