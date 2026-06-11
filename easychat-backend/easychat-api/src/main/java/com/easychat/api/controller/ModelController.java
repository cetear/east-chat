package com.easychat.api.controller;

import com.easychat.api.dto.ModelProviderDTO;
import com.easychat.api.dto.ModelRequestDTO;
import com.easychat.api.dto.ProviderDTO;
import com.easychat.common.exception.BusinessException;
import com.easychat.common.model.Result;
import com.easychat.core.service.model.ModelCommand;
import com.easychat.core.service.model.ModelManageService;
import com.easychat.core.service.model.ModelProviderCommand;
import com.easychat.core.service.model.ModelProviderView;
import com.easychat.core.service.model.ModelView;
import com.easychat.core.service.model.ProviderCommand;
import com.easychat.core.service.model.ProviderView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/model")
public class ModelController {

    @Autowired
    private ModelManageService modelManageService;

    @PostMapping("/provider/create")
    public Result<Void> addProvider(@RequestBody ProviderDTO dto) {
        try {
            modelManageService.addProvider(toCommand(dto));
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/provider/list")
    public Result<List<ProviderView>> listProviders() {
        return Result.success(modelManageService.listProviders());
    }

    @GetMapping("/provider/{id}")
    public Result<ProviderView> getProvider(@PathVariable Long id) {
        try {
            return Result.success(modelManageService.getProvider(id));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/provider/code/{providerCode}")
    public Result<ProviderView> getProviderByCode(@PathVariable String providerCode) {
        try {
            return Result.success(modelManageService.getProviderByCode(providerCode));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/provider/update")
    public Result<Void> updateProvider(@RequestBody ProviderDTO dto) {
        try {
            modelManageService.updateProvider(toCommand(dto));
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/provider/{providerCode}")
    public Result<Void> updateProviderByPath(@PathVariable String providerCode, @RequestBody ProviderDTO dto) {
        dto.setProviderCode(providerCode);
        return updateProvider(dto);
    }

    @DeleteMapping("/provider/{providerCode}")
    public Result<Void> deleteProvider(@PathVariable String providerCode) {
        try {
            modelManageService.deleteProvider(providerCode);
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/create")
    public Result<Void> addModel(@RequestBody ModelRequestDTO dto) {
        try {
            modelManageService.addModel(toCommand(dto));
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<List<ModelView>> listModels() {
        return Result.success(modelManageService.listModels());
    }

    @GetMapping("/{id}")
    public Result<ModelView> getModel(@PathVariable Long id) {
        try {
            return Result.success(modelManageService.getModel(id));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/code/{modelCode}")
    public Result<ModelView> getModelByCode(@PathVariable String modelCode) {
        try {
            return Result.success(modelManageService.getModelByCode(modelCode));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/update")
    public Result<Void> updateModel(@RequestBody ModelRequestDTO dto) {
        try {
            modelManageService.updateModel(toCommand(dto));
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{modelCode}")
    public Result<Void> updateModelByPath(@PathVariable String modelCode, @RequestBody ModelRequestDTO dto) {
        dto.setModelCode(modelCode);
        return updateModel(dto);
    }

    @PostMapping("/delete")
    public Result<Void> deleteModel(@RequestBody ModelRequestDTO dto) {
        try {
            modelManageService.deleteModel(dto.getModelCode());
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{modelCode}")
    public Result<Void> deleteModelByPath(@PathVariable String modelCode) {
        try {
            modelManageService.deleteModel(modelCode);
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/addModelToProvider")
    public Result<Void> addModelToProvider(@RequestBody ModelProviderDTO dto) {
        try {
            modelManageService.addModelToProvider(dto.getProviderCode(), toCommand(dto));
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/provider/{providerCode}/models")
    public Result<List<ModelProviderView>> listModelsByProvider(@PathVariable String providerCode) {
        return Result.success(modelManageService.listModelsByProvider(providerCode));
    }

    @GetMapping("/{modelCode}/providers")
    public Result<List<ModelProviderView>> listProvidersByModel(@PathVariable String modelCode) {
        return Result.success(modelManageService.listProvidersByModel(modelCode));
    }

    @DeleteMapping("/provider/{providerCode}/models/{modelCode}")
    public Result<Void> deleteModelProvider(@PathVariable String providerCode,
                                            @PathVariable String modelCode) {
        modelManageService.deleteModelProvider(providerCode, modelCode);
        return Result.success();
    }

    private ProviderCommand toCommand(ProviderDTO dto) {
        ProviderCommand command = new ProviderCommand();
        command.setProviderCode(dto.getProviderCode());
        command.setBaseUrl(dto.getBaseUrl());
        command.setApiKey(dto.getApiKey());
        command.setEnabled(dto.getEnabled());
        return command;
    }

    private ModelCommand toCommand(ModelRequestDTO dto) {
        ModelCommand command = new ModelCommand();
        command.setModelCode(dto.getModelCode());
        command.setModelName(dto.getModelName());
        command.setModelType(dto.getModelType());
        command.setModelFamily(dto.getModelFamily());
        command.setContextWindow(dto.getContextWindow());
        command.setMaxOutputTokens(dto.getMaxOutputTokens());
        command.setDefaultTemperature(dto.getDefaultTemperature());
        command.setDefaultTopP(dto.getDefaultTopP());
        command.setDefaultConfig(dto.getDefaultConfig());
        command.setEnabled(dto.getEnabled());
        return command;
    }

    private ModelProviderCommand toCommand(ModelProviderDTO dto) {
        ModelProviderCommand command = new ModelProviderCommand();
        command.setModelCode(dto.getModelCode());
        command.setProviderCode(dto.getProviderCode());
        command.setPriority(dto.getPriority());
        command.setWeight(dto.getWeight());
        command.setTimeoutMs(dto.getTimeoutMs());
        command.setMaxRetry(dto.getMaxRetry());
        command.setEnabled(dto.getEnabled());
        return command;
    }
}
