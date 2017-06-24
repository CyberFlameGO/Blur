/*
 * Copyright 2016 Ali Moghnieh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blurengine.blur.framework;

import com.google.common.base.Preconditions;

import com.blurengine.blur.modules.stages.StageManager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

/**
 * Module framework base class. Upon extending this class, you will need to annotate the extension with {@link ModuleInfo} to tell the framework
 * how to load this module from the config file. Immediately after implementation, ensure that the class is also registered to the
 * {@link ModuleManager} using {@link ModuleLoader#register(Class)}.
 *
 * @see #Module(ModuleManager)
 */
public abstract class Module extends AbstractComponent {

    private final ModuleInfo moduleInfo;
    private final Set<Component> subcomponents = new HashSet<>();
    final Set<Class> registeredPlayerDataClasses = new HashSet<>();

    public Module(ModuleManager moduleManager) {
        super(moduleManager);
        this.moduleInfo = ModuleLoader.getModuleInfoByModule(getClass());
    }

    /**
     * Returns sub {@link Component} stream without any {@link Module}. This is important to the stability of module loading overall.
     *
     * When a Module registers a sub module, the sub module is registered to the ModuleManager. The ModuleManager then loads that sub module as a
     * normal module. When the main Module invokes any of the state-changing methods it fails because those sub modules are already loaded and return
     * false (indicating a failure to load) due to it being loaded already.
     */
    private Stream<Component> getSubcomponentsStream() {
        return this.subcomponents.stream().filter(component -> !(component instanceof Module));
    }

    @Override
    public boolean tryLoad() {
        if (!super.tryLoad()) {
            return false;
        }
        boolean resultOfAllLoads = getSubcomponentsStream().map(Component::tryLoad).filter(b -> !b).findFirst().orElse(true);
        return resultOfAllLoads;
    }

    @Override
    public boolean tryUnload() {
        if (!super.tryUnload()) {
            return false;
        }
        boolean resultOfAllUnloads = getSubcomponentsStream().map(Component::tryUnload).filter(b -> !b).findFirst().orElse(true);
        return resultOfAllUnloads;
    }

    @Override
    public boolean tryEnable() {
        if (!super.tryEnable()) {
            return false;
        }
        boolean resultOfAllEnables = getSubcomponentsStream().map(Component::tryEnable).filter(b -> !b).findFirst().orElse(true);
        return resultOfAllEnables;
    }

    @Override
    public boolean tryDisable() {
        if (!super.tryDisable()) {
            return false;
        }
        boolean resultOfAllDisables = getSubcomponentsStream().map(Component::tryDisable).filter(b -> !b).findFirst().orElse(true);
        return resultOfAllDisables;
    }

    public ModuleInfo getModuleInfo() {
        return moduleInfo;
    }

    public boolean addSubcomponent(@Nonnull Component component) {
        Preconditions.checkNotNull(component, "component cannot be null.");
        if (component instanceof Module) {
            return addSubmodule((Module) component);
        } else {
            if (this.subcomponents.add(component)) {
                if (getState() == ComponentState.UNLOADED) {
                    return true; // Added successfully without loading.
                }

                if (component.tryLoad()) {
                    if (getState() == ComponentState.ENABLED) {
                        if (component.tryEnable()) {
                            return true; // Added, loaded, and enabled successfully.
                        } else {
                            return false; // Failed to enable
                        }
                    }
                    return true; // Added and loaded successfully.
                } else {
                    return false; // Failed to load
                }
            }
            return false;
        }
    }

    public boolean removeSubcomponent(@Nonnull Component component) {
        Preconditions.checkNotNull(component, "component cannot be null.");
        if (component instanceof Module) {
            return removeSubmodule((Module) component);
        } else {
            if (this.subcomponents.remove(component)) {
                component.tryDisable();
                component.tryUnload();
                return true;
            }
            return false;
        }
    }

    public boolean addSubmodule(@Nonnull Module module) {
        Preconditions.checkNotNull(module, "module cannot be null.");
        if (this.subcomponents.add(module)) {
            // This is a must to ensure that any added behaviour from ModuleManager is respected.
            getModuleManager().addModule(module);

            if (getState() == ComponentState.UNLOADED) {
                return true;  // Added successfully without loading.
            }

            if (getModuleManager().loadModule(module)) {
                if (getState() == ComponentState.ENABLED) {
                    if (getModuleManager().enableModule(module)) {
                        return true; // Added, loaded, and enabled successfully.
                    } else {
                        return false; // Failed to enable
                    }
                }
                return true; // Added and loaded successfully.
            } else {
                return false; // Failed to load
            }
        }
        return false;
    }

    public boolean removeSubmodule(@Nonnull Module module) {
        Preconditions.checkNotNull(module, "module cannot be null.");
        if (this.subcomponents.remove(module)) {
            getModuleManager().disableModule(module);
            getModuleManager().unloadModule(module);
            return true;
        }
        return false;
    }

    public Collection<Component> getSubcomponents() {
        return Collections.unmodifiableCollection(subcomponents);
    }

    public StageManager getStagesManager() {
        return getModuleManager().getStageManager();
    }

    public Set<Class> getRegisteredPlayerDataClasses() {
        return this.registeredPlayerDataClasses;
    }

    public void registerPlayerDataClass(Class clazz) {
        this.registeredPlayerDataClasses.add(clazz);
    }

    public boolean unregisterPlayerDataClass(Class clazz) {
        return this.registeredPlayerDataClasses.remove(clazz);
    }
}
