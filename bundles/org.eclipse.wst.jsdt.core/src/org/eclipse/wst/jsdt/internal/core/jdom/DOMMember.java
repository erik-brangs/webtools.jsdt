/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.wst.jsdt.internal.core.jdom;

import org.eclipse.wst.jsdt.core.Flags;
import org.eclipse.wst.jsdt.core.compiler.CharOperation;
import org.eclipse.wst.jsdt.core.jdom.IDOMMember;
import org.eclipse.wst.jsdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.wst.jsdt.internal.core.util.CharArrayBuffer;
/**
 * DOMMember provides an implementation of IDOMMember.
 *
 * @see IDOMMember
 * @see DOMNode
 * @deprecated The JDOM was made obsolete by the addition in 2.0 of the more
 * powerful, fine-grained DOM/AST API found in the
 * org.eclipse.wst.jsdt.core.dom package.
 */
abstract class DOMMember extends DOMNode implements IDOMMember {

	/**
	 * The modifier flags for this member that can be
	 * analyzed with org.eclipse.wst.jsdt.core.Flags
	 */
	protected int    fFlags= 0;

	/**
	 * The member's comments when it has been altered from
	 * the contents in the document, otherwise <code>null</code>.
	 */
	protected String fComment= null;

	/**
	 * The original inclusive source range of the
	 * member's preceding comments in the document,
	 * or -1's if the member did not originally have a
	 * comment.
	 */
	 protected int[] fCommentRange;


	/**
	 * The member's modifiers textual representation when
	 * the modifiers (flags) have been altered from
	 * their original contents, otherwise <code>null</code>.
	 */
	 protected char[] fModifiers= null;

	/**
	 * The original inclusive source range of the
	 * member's modifiers in the document, or -1's if
	 * the member did not originally have modifiers in
	 * the source code (that is, package default visibility).
	 */
	 protected int[] fModifierRange;

/**
 * Constructs an empty member node.
 */
DOMMember() {
	// Constructs an empty member node
}
/**
 * Creates a new member document fragment on the given range of the document.
 *
 * @param document - the document containing this node's original contents
 * @param sourceRange - a two element array of integers describing the
 *		entire inclusive source range of this node within its document.
 * 		Contents start on and include the character at the first position.
 *		Contents end on and include the character at the last position.
 *		An array of -1's indicates this node's contents do not exist
 *		in the document.
 * @param name - the identifier portion of the name of this node, or
 *		<code>null</code> if this node does not have a name
 * @param nameRange - a two element array of integers describing the
 *		entire inclusive source range of this node's name within its document,
 *		including any array qualifiers that might immediately follow the name.
 * @param commentRange - a two element array describing the comments that precede
 *		the member declaration. The first matches the start of this node's
 *		sourceRange, and the second is the new-line or first non-whitespace
 *		character following the last comment. If no comments are present,
 *		this array contains two -1's.
 * @param flags - an integer representing the modifiers for this member. The
 *		integer can be analyzed with org.eclipse.wst.jsdt.core.Flags
 * @param modifierRange - a two element array describing the location of
 *		modifiers for this member within its source range. The first integer
 *		is the first character of the first modifier for this member, and
 *		the second integer is the last whitespace character preceeding the
 *		next part of this member declaration. If there are no modifiers present
 *		in this node's source code (that is, package default visibility), this array
 *		contains two -1's.
 */
DOMMember(char[] document, int[] sourceRange, String name, int[] nameRange, int[] commentRange, int flags, int[] modifierRange) {
	super(document, sourceRange, name, nameRange);
	fFlags= flags;
	fComment= null;
	fCommentRange= commentRange;
	fModifierRange= modifierRange;
	setHasComment(commentRange[0] >= 0);
}
/**
 * Appends the contents of this node to the given CharArrayBuffer, using
 * the original document and indicies as a form for the current attribute values
 * of this node.
 *
 * <p>To facilitate the implementation of generating contents for members,
 * the content of members is split into three sections - the header,
 * declaration, and body sections. The header section includes any preceding
 * comments and modifiers. The declaration section includes the portion of
 * the member declaration that follows any modifiers and precedes the
 * member body. The body section includes the member body and any trailing
 * whitespace.
 *
 * @see DOMNode#appendFragmentedContents(CharArrayBuffer)
 */
protected void appendFragmentedContents(CharArrayBuffer buffer) {
	if (isDetailed()) {
		appendMemberHeaderFragment(buffer);
		appendMemberDeclarationContents(buffer);
		appendMemberBodyContents(buffer);
	} else {
		appendSimpleContents(buffer);
	}
}
/**
 * Appends this member's body contents to the given CharArrayBuffer.
 * Body contents include the member body and any trailing whitespace.
 */
protected abstract void appendMemberBodyContents(CharArrayBuffer buffer);
/**
 * Appends this member's declaration contents to the given CharArrayBuffer.
 * The declaration contents includes the portion of this member that
 * appears after any modifiers and precedes the body.
 */
protected abstract void appendMemberDeclarationContents(CharArrayBuffer buffer);
/**
 * Appends this member's header contents to the given CharArrayBuffer.
 * Header contents include any preceding comments and modifiers.
 */
protected void appendMemberHeaderFragment(CharArrayBuffer buffer) {

	int spaceStart, spaceEnd;

	// space before comment
	if (hasComment()) {
		spaceStart= fSourceRange[0];
		spaceEnd= fCommentRange[0];
		if (spaceEnd > 0) {
			buffer.append(fDocument, spaceStart, spaceEnd - spaceStart);
		}
	}

	String fragment= getComment();
	if (fragment != null) {
		buffer.append(fragment);
	}

	if (fCommentRange[1] >= 0) {
		spaceStart= fCommentRange[1] + 1;
	} else {
		spaceStart= fSourceRange[0];
	}
	if (fModifierRange[0] >= 0) {
		spaceEnd= fModifierRange[0] - 1;
	} else {
		spaceEnd= getMemberDeclarationStartPosition() - 1;
	}

	if (spaceEnd >= spaceStart) {
		buffer.append(fDocument, spaceStart, spaceEnd + 1 - spaceStart);
	}
	buffer.append(getModifiersText());

}
/**
 * Appends the contents of this node to the given CharArrayBuffer, using
 * the original document and indicies as a form for the current attribute values
 * of this node. This method is called when this node is know not to have
 * detailed source indexes.
 */
protected abstract void appendSimpleContents(CharArrayBuffer buffer);
/**
 * Returns a copy of the given array with the new element appended
 * to the end of the array.
 */
protected String[] appendString(String[] list, String element) {
	String[] copy= new String[list.length + 1];
	System.arraycopy(list, 0, copy, 0, list.length);
	copy[list.length]= element;
	return copy;
}
/**
 * Returns a <code>String</code> describing the modifiers for this member,
 * ending with whitespace (if not empty). This value serves as a replacement
 * value for the member's modifier range when the modifiers have been altered
 * from their original contents.
 */
protected char[] generateFlags() {
	char[] flags= Flags.toString(getFlags()).toCharArray();
	if (flags.length == 0) {
		return flags;
	} else {
		return CharOperation.concat(flags, new char[] {' '});
	}
}
/**
 * @see IDOMMember#getComment()
 */
public String getComment() {
	becomeDetailed();
	if (hasComment()) {
		if (fComment != null) {
			return fComment;
		} else {
			return new String(fDocument, fCommentRange[0], fCommentRange[1] + 1 - fCommentRange[0]);
		}
	} else {
		return null;
	}
}
/**
 * @see IDOMMember#getFlags()
 */
public int getFlags() {
	return fFlags;
}
/**
 * Returns the location of the first character in the member's declaration
 * section.
 */
protected abstract int getMemberDeclarationStartPosition();
/**
 * Returns the String to be used for this member's flags when
 * generating contents - either the original contents in the document
 * or the replacement value.
 */
protected char[] getModifiersText() {
	if (fModifiers == null) {
		if (fModifierRange[0] < 0) {
			return null;
		} else {
			return CharOperation.subarray(fDocument, fModifierRange[0], fModifierRange[1] + 1);
		}
	} else {
		return fModifiers;
	}
}
/**
 * Returns true if this member currently has a body.
 */
protected boolean hasBody() {
	return getMask(MASK_HAS_BODY);
}
/**
 * Returns true if this member currently has a comment.
 */
protected boolean hasComment() {
	return getMask(MASK_HAS_COMMENT);
}
/**
 * Offsets all the source indexes in this node by the given amount.
 */
protected void offset(int offset) {
	super.offset(offset);
	offsetRange(fCommentRange, offset);
	offsetRange(fModifierRange, offset);
}
/**
 * @see IDOMMember#setComment(String)
 */
public void setComment(String comment) {
	becomeDetailed();
	fComment= comment;
	fragment();
	setHasComment(comment != null);
	/* see 1FVIJAH */
	if (comment != null && comment.indexOf("@deprecated") >= 0) { //$NON-NLS-1$
		fFlags= fFlags | ClassFileConstants.AccDeprecated;
		return;
	}
	fFlags= fFlags & (~ClassFileConstants.AccDeprecated);
}
/**
 * @see IDOMMember#setFlags(int)
 */
public void setFlags(int flags) {
	becomeDetailed();
	if (Flags.isDeprecated(fFlags)) {
		fFlags= flags | ClassFileConstants.AccDeprecated;
	} else {
		fFlags= flags & (~ClassFileConstants.AccDeprecated);
	}
	fragment();
	fModifiers= generateFlags();
}
/**
 * Sets the state of this member declaration as having
 * a body.
 */
protected void setHasBody(boolean hasBody) {
	setMask(MASK_HAS_BODY, hasBody);
}
/**
 * Sets the state of this member declaration as having
 * a preceding comment.
 */
protected void setHasComment(boolean hasComment) {
	setMask(MASK_HAS_COMMENT, hasComment);
}
/**
 * Sets the original position of the first character of this node's contents
 * in its document. This method is only used during DOM creation while
 * normalizing the source range of each node.
 *
 * Synchronize the start of the comment position with the start of the
 * node.
 */
protected void setStartPosition(int start) {
	if (fCommentRange[0] >= 0) {
		fCommentRange[0]= start;
	}
	super.setStartPosition(start);
}
/**
 * @see DOMNode#shareContents(DOMNode)
 */
protected void shareContents(DOMNode node) {
	super.shareContents(node);
	DOMMember member= (DOMMember)node;
	fComment= member.fComment;
	fCommentRange= rangeCopy(member.fCommentRange);
	fFlags= member.fFlags;
	fModifiers= member.fModifiers;
	fModifierRange= rangeCopy(member.fModifierRange);
}
}
