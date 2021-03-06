package org.apache.qpid.transport;
/*
 *
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
 *
 */


public enum SegmentType {

    CONTROL((short) 0),
    COMMAND((short) 1),
    HEADER((short) 2),
    BODY((short) 3);

    private final short value;

    SegmentType(short value)
    {
        this.value = value;
    }

    public short getValue()
    {
        return value;
    }

    public static SegmentType get(short value)
    {
        switch (value)
        {
        case (short) 0: return CONTROL;
        case (short) 1: return COMMAND;
        case (short) 2: return HEADER;
        case (short) 3: return BODY;
        default: throw new IllegalArgumentException("no such value: " + value);
        }
    }
}
