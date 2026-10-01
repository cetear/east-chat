package com.easychat.infra.mysql.mapper;
import org.apache.ibatis.annotations.*;
@Mapper public interface OperationsMapper { @Select("SELECT 1") int ping(); }
