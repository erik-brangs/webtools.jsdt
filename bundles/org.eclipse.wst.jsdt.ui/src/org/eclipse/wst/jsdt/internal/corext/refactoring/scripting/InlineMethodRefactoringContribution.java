/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.wst.jsdt.internal.corext.refactoring.scripting;

import java.util.StringTokenizer;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.participants.RefactoringArguments;
import org.eclipse.wst.jsdt.core.ICompilationUnit;
import org.eclipse.wst.jsdt.core.IJavaElement;
import org.eclipse.wst.jsdt.core.IMethod;
import org.eclipse.wst.jsdt.core.ISourceRange;
import org.eclipse.wst.jsdt.core.JavaModelException;
import org.eclipse.wst.jsdt.core.dom.AST;
import org.eclipse.wst.jsdt.core.dom.ASTParser;
import org.eclipse.wst.jsdt.core.dom.CompilationUnit;
import org.eclipse.wst.jsdt.core.refactoring.IJavaRefactorings;
import org.eclipse.wst.jsdt.internal.corext.refactoring.JDTRefactoringContribution;
import org.eclipse.wst.jsdt.internal.corext.refactoring.JDTRefactoringDescriptor;
import org.eclipse.wst.jsdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.wst.jsdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.wst.jsdt.internal.corext.refactoring.code.InlineMethodRefactoring;
import org.eclipse.wst.jsdt.internal.corext.util.Messages;
import org.eclipse.wst.jsdt.internal.ui.JavaPlugin;

/**
 * Refactoring contribution for the inline method refactoring.
 * 
 * @since 3.2
 */
public final class InlineMethodRefactoringContribution extends JDTRefactoringContribution {

	/**
	 * {@inheritDoc}
	 */
	public final Refactoring createRefactoring(final RefactoringDescriptor descriptor) throws CoreException {
		int selectionStart= -1;
		int selectionLength= -1;
		ICompilationUnit unit= null;
		CompilationUnit node= null;
		RefactoringArguments arguments= null;
		if (descriptor instanceof JDTRefactoringDescriptor) {
			final JDTRefactoringDescriptor extended= (JDTRefactoringDescriptor) descriptor;
			arguments= extended.createArguments();
		}
		if (arguments instanceof JavaRefactoringArguments) {
			final JavaRefactoringArguments extended= (JavaRefactoringArguments) arguments;
			final String selection= extended.getAttribute(JDTRefactoringDescriptor.ATTRIBUTE_SELECTION);
			if (selection != null) {
				int offset= -1;
				int length= -1;
				final StringTokenizer tokenizer= new StringTokenizer(selection);
				if (tokenizer.hasMoreTokens())
					offset= Integer.valueOf(tokenizer.nextToken()).intValue();
				if (tokenizer.hasMoreTokens())
					length= Integer.valueOf(tokenizer.nextToken()).intValue();
				if (offset >= 0 && length >= 0) {
					selectionStart= offset;
					selectionLength= length;
				} else
					throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), 0, Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { selection, JDTRefactoringDescriptor.ATTRIBUTE_SELECTION}), null));
			}
			final String handle= extended.getAttribute(JDTRefactoringDescriptor.ATTRIBUTE_INPUT);
			if (handle != null) {
				final IJavaElement element= JDTRefactoringDescriptor.handleToElement(extended.getProject(), handle, false);
				if (element == null || !element.exists())
					throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), 0, Messages.format(RefactoringCoreMessages.InitializableRefactoring_inputs_do_not_exist, new String[] { RefactoringCoreMessages.InlineMethodRefactoring_name, IJavaRefactorings.INLINE_METHOD}), null));
				else {
					if (element instanceof ICompilationUnit) {
						unit= (ICompilationUnit) element;
						if (selection == null)
							throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), 0, Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JDTRefactoringDescriptor.ATTRIBUTE_SELECTION), null));
					} else if (element instanceof IMethod) {
						final IMethod method= (IMethod) element;
						try {
							final ISourceRange range= method.getNameRange();
							if (range != null) {
								selectionStart= range.getOffset();
								selectionLength= range.getLength();
							} else
								throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), 0, Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { handle, JDTRefactoringDescriptor.ATTRIBUTE_INPUT}), null));
						} catch (JavaModelException exception) {
							throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), 0, Messages.format(RefactoringCoreMessages.InitializableRefactoring_inputs_do_not_exist, new String[] { RefactoringCoreMessages.InlineMethodRefactoring_name, IJavaRefactorings.INLINE_METHOD}), exception));
						}
						unit= method.getCompilationUnit();
					} else
						throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), 0, Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { handle, JDTRefactoringDescriptor.ATTRIBUTE_INPUT}), null));
					final ASTParser parser= ASTParser.newParser(AST.JLS3);
					parser.setResolveBindings(true);
					parser.setSource(unit);
					node= (CompilationUnit) parser.createAST(null);
				}
			} else
				throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), 0, Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JDTRefactoringDescriptor.ATTRIBUTE_INPUT), null));
		} else
			throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), 0, RefactoringCoreMessages.InitializableRefactoring_inacceptable_arguments, null));
		return InlineMethodRefactoring.create(unit, node, selectionStart, selectionLength);
	}
}
