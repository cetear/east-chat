package com.easychat.infra.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easychat.infra.mysql.entity.ModelProviderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ModelProviderMapper extends BaseMapper<ModelProviderDO> {

    @Select("SELECT mp.*, p.base_url, p.api_key " +
            "FROM model_provider mp " +
            "JOIN provider p ON mp.provider_code = p.provider_code " +
            "WHERE mp.model_code = #{modelCode} AND mp.enabled = 1 AND p.enabled = 1 " +
            "ORDER BY mp.priority ASC")
    List<ModelProviderDO> findEnabledByModelCode(@Param("modelCode") String modelCode);
}
