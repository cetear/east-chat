package com.easychat.infra.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easychat.infra.mysql.entity.ChatMessageDO;
import com.easychat.infra.mysql.entity.ChatRequestLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatRequestLogMapper extends BaseMapper<ChatRequestLogDO> {
}
