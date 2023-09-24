/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.rat.mp;

import org.apache.maven.plugins.annotations.Parameter;
import org.apache.rat.analysis.IHeaderMatcher;

abstract class EnclosingMatcher {

    protected abstract void setBuilder(IHeaderMatcher.Builder builder);

    @Parameter(required = false)
    public void setAnd(All all) {
        setBuilder(all);
    }

    @Parameter(required = false)
    public void setOr(Any any) {
        setBuilder(any);
    }

    @Parameter(required = false)
    public void setText(Text text) {
        setBuilder(text);
    }

    @Parameter(required = false)
    public void setCopyright(Copyright copyright) {
        setBuilder(copyright);
    }

    @Parameter(required = false)
    public void setRegex(Regex regex) {
        setBuilder(regex);
    }

    @Parameter(required = false)
    public void setSpdx(Spdx spdx) {
        setBuilder(spdx);
    }
}
