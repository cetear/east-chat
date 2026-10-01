package com.easychat.infra.mysql.repository;
import com.easychat.common.domain.model.ModelRoute;
import com.easychat.common.domain.model.ProviderAccount;
import com.easychat.common.domain.model.ModelDefinition;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.easychat.common.entity.*;
import com.easychat.infra.mysql.mapper.*;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.time.LocalDateTime;
@Repository @RequiredArgsConstructor
public class RouteCatalog {
 private final ModelMapper models;
 private final ProviderMapper providers;
 private final ModelProviderMapper routes;
 public List<ModelDefinition> models(){return models.selectList(new LambdaQueryWrapper<ModelDefinition>().eq(ModelDefinition::getEnabled,1));}
 public List<ProviderAccount> providers(){return providers.selectList(new LambdaQueryWrapper<ProviderAccount>().eq(ProviderAccount::getEnabled,1));}
 public List<ModelRoute> routes(){return routes.selectList(new LambdaQueryWrapper<ModelRoute>().eq(ModelRoute::getEnabled,1));}
 public boolean enabled(String code){return code!=null&&models.selectCount(new LambdaQueryWrapper<ModelDefinition>().eq(ModelDefinition::getModelCode,code).eq(ModelDefinition::getEnabled,1))>0;}
 public void snapshot(String code,int failures,String state,LocalDateTime last){providers.update(null,new UpdateWrapper<ProviderAccount>().eq("provider_code",code).set("fail_count",failures).set("circuit_status",state).set("last_fail_time",last));}
}
