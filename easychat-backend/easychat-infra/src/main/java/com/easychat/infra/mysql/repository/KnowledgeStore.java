package com.easychat.infra.mysql.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.easychat.common.entity.KnowledgeDocDO;
import com.easychat.infra.mysql.mapper.KnowledgeDocMapper;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
@Repository @RequiredArgsConstructor
public class KnowledgeStore {
 private final KnowledgeDocMapper mapper;
 public KnowledgeDocDO find(String code){return mapper.selectOne(new LambdaQueryWrapper<KnowledgeDocDO>().eq(KnowledgeDocDO::getDocCode,code));}
 public KnowledgeDocDO owned(String code,String owner){return mapper.selectOne(new LambdaQueryWrapper<KnowledgeDocDO>().eq(KnowledgeDocDO::getDocCode,code).eq(KnowledgeDocDO::getOwnerId,owner));}
 public KnowledgeDocDO duplicate(String dataset,String owner,String hash){return mapper.selectOne(new LambdaQueryWrapper<KnowledgeDocDO>().eq(KnowledgeDocDO::getDataset,dataset).eq(KnowledgeDocDO::getOwnerId,owner).eq(KnowledgeDocDO::getFileHash,hash).last("LIMIT 1"));}
 public List<KnowledgeDocDO> list(String owner,String dataset){var query=new LambdaQueryWrapper<KnowledgeDocDO>().eq(KnowledgeDocDO::getOwnerId,owner).orderByDesc(KnowledgeDocDO::getCreatedAt);if(dataset!=null&&!dataset.isBlank())query.eq(KnowledgeDocDO::getDataset,dataset.trim());return mapper.selectList(query);}
 public int insert(KnowledgeDocDO doc){return mapper.insert(doc);}
 public int update(KnowledgeDocDO doc){return mapper.updateById(doc);}
 public int delete(Long id){return mapper.deleteById(id);}
 public int claim(Long id){return mapper.update(null,new LambdaUpdateWrapper<KnowledgeDocDO>().eq(KnowledgeDocDO::getId,id).eq(KnowledgeDocDO::getStatus,com.easychat.common.constant.DocumentStatus.PENDING).set(KnowledgeDocDO::getStatus,com.easychat.common.constant.DocumentStatus.INDEXING).set(KnowledgeDocDO::getErrorMsg,null));}
 public void outcome(KnowledgeDocDO doc,String status,String error){doc.setStatus(status);doc.setErrorMsg(null);doc.setUpdatedAt(LocalDateTime.now());mapper.update(doc,new LambdaUpdateWrapper<KnowledgeDocDO>().eq(KnowledgeDocDO::getId,doc.getId()).set(KnowledgeDocDO::getErrorMsg,error==null?null:error.substring(0,Math.min(500,error.length()))));}
 public List<KnowledgeDocDO> pending(){return mapper.selectList(new LambdaQueryWrapper<KnowledgeDocDO>().eq(KnowledgeDocDO::getStatus,com.easychat.common.constant.DocumentStatus.PENDING).last("LIMIT 100"));}
 public void recoverExpired(){mapper.recoverExpired();}
 public void recoverLocal(){mapper.update(null,new LambdaUpdateWrapper<KnowledgeDocDO>().eq(KnowledgeDocDO::getStatus,com.easychat.common.constant.DocumentStatus.INDEXING).set(KnowledgeDocDO::getStatus,com.easychat.common.constant.DocumentStatus.PENDING));mapper.update(null,new LambdaUpdateWrapper<KnowledgeDocDO>().eq(KnowledgeDocDO::getStatus,com.easychat.common.constant.DocumentStatus.DELETING).set(KnowledgeDocDO::getStatus,com.easychat.common.constant.DocumentStatus.DELETE_FAILED));}
}
