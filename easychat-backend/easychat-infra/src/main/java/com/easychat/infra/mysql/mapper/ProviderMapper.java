package com.easychat.infra.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easychat.common.domain.model.ProviderAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProviderMapper extends BaseMapper<ProviderAccount> {
}
