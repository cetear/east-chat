package com.easychat.api.controller;

import com.easychat.api.dto.ModelProviderDTO;
import com.easychat.api.dto.ModelRequestDTO;
import com.easychat.api.dto.ProviderDTO;
import com.easychat.common.exception.BusinessException;
import com.easychat.common.model.Result;
import com.easychat.core.service.model.ModelCommand;
import com.easychat.core.service.model.ModelManageService;
import com.easychat.core.service.model.ModelProviderCommand;
import com.easychat.api.dto.ModelProviderView;
import com.easychat.api.dto.ModelView;
import com.easychat.core.service.model.ProviderCommand;
import com.easychat.api.dto.ProviderView;
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
        modelManageService.addProvider(toCommand(dto));
        return Result.success();

    }

    @GetMapping("/provider/list")
    public Result<List<ProviderView>> listProviders() {
        return Result.success(modelManageService.listProviders().stream().map(ProviderView::from).toList());
    }

    @GetMapping("/provider/{id}")
    public Result<ProviderView> getProvider(@PathVariable("id") Long id) {
        return Result.success(ProviderView.from(modelManageService.getProvider(id)));

    }

    @GetMapping("/provider/code/{providerCode}")
    public Result<ProviderView> getProviderByCode(@PathVariable("providerCode") String providerCode) {
        return Result.success(ProviderView.from(modelManageService.getProviderByCode(providerCode)));

    }

    @PostMapping("/provider/update")
    public Result<Void> updateProvider(@RequestBody ProviderDTO dto) {
        modelManageService.updateProvider(toCommand(dto));
        return Result.success();

    }

    @PutMapping("/provider/{providerCode}")
    public Result<Void> updateProviderByPath(@PathVariable("providerCode") String providerCode, @RequestBody ProviderDTO dto) {
        dto.setProviderCode(providerCode);
        return updateProvider(dto);
    }

    @DeleteMapping("/provider/{providerCode}")
    public Result<Void> deleteProvider(@PathVariable("providerCode") String providerCode) {
        modelManageService.deleteProvider(providerCode);
        return Result.success();

    }

    @PostMapping("/create")
    public Result<Void> addModel(@RequestBody ModelRequestDTO dto) {
        modelManageService.addModel(toCommand(dto));
        return Result.success();

    }

    @GetMapping("/list")
    public Result<List<ModelView>> listModels() {
        return Result.success(modelManageService.listModels().stream().map(ModelView::from).toList());
    }

    @GetMapping("/{id}")
    public Result<ModelView> getModel(@PathVariable("id") Long id) {
        return Result.success(ModelView.from(modelManageService.getModel(id)));

    }

    @GetMapping("/code/{modelCode}")
    public Result<ModelView> getModelByCode(@PathVariable("modelCode") String modelCode) {
        return Result.success(ModelView.from(modelManageService.getModelByCode(modelCode)));

    }

    @PostMapping("/update")
    public Result<Void> updateModel(@RequestBody ModelRequestDTO dto) {
        modelManageService.updateModel(toCommand(dto));
        return Result.success();

    }

    @PutMapping("/{modelCode}")
    public Result<Void> updateModelByPath(@PathVariable("modelCode") String modelCode, @RequestBody ModelRequestDTO dto) {
        dto.setModelCode(modelCode);
        return updateModel(dto);
    }

    @PostMapping("/delete")
    public Result<Void> deleteModel(@RequestBody ModelRequestDTO dto) {
        modelManageService.deleteModel(dto.getModelCode());
        return Result.success();

    }

    @DeleteMapping("/{modelCode}")
    public Result<Void> deleteModelByPath(@PathVariable("modelCode") String modelCode) {
        modelManageService.deleteModel(modelCode);
        return Result.success();

    }

    @PostMapping("/addModelToProvider")
    public Result<Void> addModelToProvider(@RequestBody ModelProviderDTO dto) {
        modelManageService.addModelToProvider(dto.getProviderCode(), toCommand(dto));
        return Result.success();

    }

    @GetMapping("/provider/{providerCode}/models")
    public Result<List<ModelProviderView>> listModelsByProvider(@PathVariable("providerCode") String providerCode) {
        return Result.success(modelManageService.listModelsByProvider(providerCode).stream().map(ModelProviderView::from).toList());
    }

    @GetMapping("/{modelCode}/providers")
    public Result<List<ModelProviderView>> listProvidersByModel(@PathVariable("modelCode") String modelCode) {
        return Result.success(modelManageService.listProvidersByModel(modelCode).stream().map(ModelProviderView::from).toList());
    }

    @DeleteMapping("/provider/{providerCode}/models/{modelCode}")
    public Result<Void> deleteModelProvider(@PathVariable("providerCode") String providerCode,
                                            @PathVariable("modelCode") String modelCode) {
        modelManageService.deleteModelProvider(providerCode, modelCode);
        return Result.success();
    }

    @PutMapping("/provider/{providerCode}/models/{modelCode}")
    public Result<Void> updateModelProvider(@PathVariable("providerCode") String providerCode,
                                            @PathVariable("modelCode") String modelCode, @RequestBody ModelProviderDTO dto) {
        modelManageService.updateModelProvider(providerCode, modelCode, toCommand(dto));
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
        command.setSupportVision(dto.getSupportVision());
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
