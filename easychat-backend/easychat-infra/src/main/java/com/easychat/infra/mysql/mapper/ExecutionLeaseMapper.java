package com.easychat.infra.mysql.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easychat.common.entity.ExecutionLeaseDO;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface ExecutionLeaseMapper extends BaseMapper<ExecutionLeaseDO> {
 @Insert("INSERT IGNORE INTO execution_lease(lock_key,owner_token,expires_at) VALUES (#{key}, '', NOW(3))") int ensure(String key);
 @Update("UPDATE execution_lease SET owner_token=#{token}, expires_at=TIMESTAMPADD(SECOND,60,NOW(3)) WHERE lock_key=#{key} AND expires_at<=NOW(3)") int acquire(@Param("key") String key,@Param("token") String token);
 @Update("UPDATE execution_lease SET expires_at=TIMESTAMPADD(SECOND,60,NOW(3)) WHERE lock_key=#{key} AND owner_token=#{token} AND expires_at>NOW(3)") int renew(@Param("key") String key,@Param("token") String token);
 @Select("SELECT COUNT(*) FROM execution_lease WHERE lock_key=#{key} AND owner_token=#{token} AND expires_at>NOW(3)") int valid(@Param("key") String key,@Param("token") String token);
 @Select("SELECT owner_token FROM execution_lease WHERE lock_key=#{key} AND expires_at>NOW(3) FOR UPDATE") List<String> lockOwner(String key);
 @Update("UPDATE execution_lease SET expires_at=NOW(3) WHERE lock_key=#{key} AND owner_token=#{token}") int release(@Param("key") String key,@Param("token") String token);
 @Select("SELECT COUNT(*) FROM execution_lease WHERE expires_at>NOW(3)") long activeCount();
}
