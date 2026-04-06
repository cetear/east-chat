package com.easychat.infra.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easychat.infra.mysql.entity.ModelConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigDO> {
}
