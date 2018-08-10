/* 
 * The MIT License
 *
 * Copyright 2018 Milé Buurmeijer <mbuurmei at netscape.net>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.eclipse.milo.opcua.sdk.server.api.nodes.VariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.delegates.AttributeDelegate;
import org.eclipse.milo.opcua.sdk.server.nodes.delegates.DelegatingAttributeDelegate;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;

public class ValueLoggingDelegate extends DelegatingAttributeDelegate {

    public ValueLoggingDelegate() {}

    public ValueLoggingDelegate(@Nullable AttributeDelegate parent) {
        super(parent);
    }

    @Override
    public DataValue getValue(AttributeContext context, VariableNode node) throws UaException {
        DataValue value = super.getValue(context, node);

        // only log external reads
        if (context.getSession().isPresent()) {
            Logger.getLogger( ValueLoggingDelegate.class.getName()).log(Level.INFO, "getValue() nodeId=" + node.getNodeId() + " value=" + value);
        }

        return value;
    }

    @Override
    public void setValue(AttributeContext context, VariableNode node, DataValue value) throws UaException {
        // only log external writes
        if (context.getSession().isPresent()) {
            Logger.getLogger( ValueLoggingDelegate.class.getName()).log(Level.INFO, "setValue() nodeId=" + node.getNodeId() + " value=" + value);
        }

        super.setValue(context, node, value);
    }
}
