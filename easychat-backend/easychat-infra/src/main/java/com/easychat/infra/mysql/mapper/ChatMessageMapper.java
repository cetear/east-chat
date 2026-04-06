package com.easychat.infra.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easychat.infra.mysql.entity.ChatMessageDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageDO> {
}
