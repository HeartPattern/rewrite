/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.maven;

import org.openrewrite.Validated;
import org.openrewrite.maven.tree.Maven;

import static org.openrewrite.Validated.required;

public class ChangeParentVersion extends MavenRefactorVisitor {
    private String groupId;
    private String artifactId;
    private String toVersion;

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public void setToVersion(String toVersion) {
        this.toVersion = toVersion;
    }

    @Override
    public Validated validate() {
        return required("groupId", groupId)
                .and(required("artifactId", artifactId))
                .and(required("toVersion", toVersion));
    }

    @Override
    public Maven visitParent(Maven.Parent parent) {
        Maven.Parent p = refactor(parent, super::visitParent);
        if (groupId.equals(p.getGroupId()) && artifactId.equals(p.getArtifactId()) &&
                !toVersion.equals(p.getVersion())) {
            p = p.withVersion(toVersion);
        }
        return p;
    }
}
