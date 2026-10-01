package com.easychat.infra.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.easychat.common.entity.KnowledgeDocDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDocDO> {

 @org.apache.ibatis.annotations.Update("UPDATE knowledge_doc d SET status=CASE WHEN status='INDEXING' THEN 'PENDING' ELSE 'DELETE_FAILED' END WHERE status IN ('INDEXING','DELETING') AND NOT EXISTS (SELECT 1 FROM execution_lease l WHERE l.lock_key=CONCAT('doc:',d.doc_code) AND l.expires_at>NOW(3))")
 int recoverExpired();
 @org.apache.ibatis.annotations.Select("SELECT status,COUNT(*) AS count FROM knowledge_doc GROUP BY status")
 java.util.List<java.util.Map<String,Object>> counts();

}
