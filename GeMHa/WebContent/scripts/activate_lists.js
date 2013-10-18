/*
 *
 */    

function addListeners() {
	addListenersForColumnNames();
	addListenersForMindElements();
	addListenersForSendElements();
	addListenersForAuditElements();
}
	
// Add listener to an li of the Input Column Names list
function addListenerToNewColumnName(newLi) {
	// This adds the click-event handler for the Input Column Names items
    $(newLi).click( function() {
    	$("div#columnOrderListButtons input[type=button]").attr("disabled", false);
    	
    	$("#columnOrderList li").removeClass('selected');
        $(this).addClass('selected');
    } );

}

// Add listeners to the Input Column Name buttons
function addListenersForColumnNames() {
	var columnOrderList = $("#columnOrderList"); 
	
    // This adds the click-event handler for the Input Column Name Add button
    $("#columnOrderListAdd").click( function() {
    	var newColumnName = $("#inputNewColName").val();
    	if (newColumnName.length > 0) {
       		$(columnOrderList).append("<li>" + newColumnName + "</li>");
       		addListenerToNewColumnName($("#columnOrderList li:last"));
       		$("#inputNewColName").val("");

       		validateRequiredList(columnOrderList);
    	}
    } );

    // This adds the click-event handler for the Input Column Name Up button
    $("#columnOrderListUp").click( function() {
		var currentlySelectedLi = $("#columnOrderList li.selected");
		var currentlySelectedLiIndex = $(currentlySelectedLi).index();
    	if (currentlySelectedLiIndex > 0) {
    		// Move the selected li up one (note "before" is actually a move)
    		$("#columnOrderList li:eq(" + (currentlySelectedLiIndex-1) + ")").before(currentlySelectedLi);
    	}
    } );

    // This adds the click-event handler for the Input Column Name Down button
    $("#columnOrderListDown").click( function() {
		var currentlySelectedLi = $("#columnOrderList li.selected");
		var currentlySelectedLiIndex = $(currentlySelectedLi).index();
		
    	if (currentlySelectedLiIndex < ($('#columnOrderList li').length-1) ) {
    		// Move the selected li down one (note "before" is actually a move)
    		$("#columnOrderList li:eq(" + (currentlySelectedLiIndex+1) + ")").after(currentlySelectedLi);
    	}
    } );

    // This adds the click-event handler for the Input Column Name Remove button
    $("#columnOrderListRemove").click( function() {
		var currentlySelectedLi = $("#columnOrderList li.selected");
		var currentlySelectedLiText = $(currentlySelectedLi).text();
   		$(currentlySelectedLi).remove();

   		$("#inputNewColName").val(currentlySelectedLiText);
   		
   		validateRequiredList(columnOrderList);
    } );

}

// Add listener to an li of the Mind Elements list
function addListenerToNewMindElement(newLi) {
	// This adds the click-event handler for the Input Column Names items
    $(newLi).click( function() {
    	$("div#mindElementListButtons input[type=button]").attr("disabled", false);
    	
    	$("#mindElementList li").removeClass('selected');
        $(this).addClass('selected');
    } );

}

// Add listeners to the Mind Elements buttons
function addListenersForMindElements() {
	
    // This adds the click-event handler for the Mind Elements Add button
    $("#mindElementListAdd").click( function() {
    	var newMindElement = $("#inputNewMindElement").val();
    	if (newMindElement.length > 0) {
       		$("#mindElementList").append("<li>" + newMindElement + "</li>");
       		addListenerToNewMindElement($("#mindElementList li:last"));
       		$("#inputNewMindElement").val("");
    	}
    } );

    // This adds the click-event handler for the Mind Elements Up button
    $("#mindElementListUp").click( function() {
		var currentlySelectedLi = $("#mindElementList li.selected");
		var currentlySelectedLiIndex = $(currentlySelectedLi).index();
    	if (currentlySelectedLiIndex > 0) {
    		// Move the selected li up one (note "before" is actually a move)
    		$("#mindElementList li:eq(" + (currentlySelectedLiIndex-1) + ")").before(currentlySelectedLi);
    	}
    } );

    // This adds the click-event handler for the Mind Elements Down button
    $("#mindElementListDown").click( function() {
		var currentlySelectedLi = $("#mindElementList li.selected");
		var currentlySelectedLiIndex = $(currentlySelectedLi).index();
		
    	if (currentlySelectedLiIndex < ($('#mindElementList li').length-1) ) {
    		// Move the selected li down one (note "before" is actually a move)
    		$("#mindElementList li:eq(" + (currentlySelectedLiIndex+1) + ")").after(currentlySelectedLi);
    	}
    } );

    // This adds the click-event handler for the Mind Elements Remove button
    $("#mindElementListRemove").click( function() {
		var currentlySelectedLi = $("#mindElementList li.selected");
		var currentlySelectedLiText = $(currentlySelectedLi).text();
   		$(currentlySelectedLi).remove();

   		$("#inputNewMindElement").val(currentlySelectedLiText);
    } );

}

// Add listener to an li of the Send Elements list
function addListenerToNewSendElement(newLi) {
	// This adds the click-event handler for the Send Elements items
    $(newLi).click( function() {
    	$("div#sendElementListButtons input[type=button]").attr("disabled", false);
    	
    	$("#sendElementList li").removeClass('selected');
        $(this).addClass('selected');
    } );

}

// Add listeners to the Send Elements buttons
function addListenersForSendElements() {
	var sendElementList = $("#sendElementList"); 
	
    // This adds the click-event handler for the Send Elements Add button
    $("#sendElementListAdd").click( function() {
    	var newSendElement = $("#inputNewSendElement").val();
    	if (newSendElement.length > 0) {
       		$("#sendElementList").append("<li>" + newSendElement + "</li>");
       		addListenerToNewSendElement($("#sendElementList li:last"));
       		$("#inputNewSendElement").val("");

       		validateRequiredList(sendElementList);
    	}
    } );

    // This adds the click-event handler for the Send Elements Up button
    $("#sendElementListUp").click( function() {
		var currentlySelectedLi = $("#sendElementList li.selected");
		var currentlySelectedLiIndex = $(currentlySelectedLi).index();
    	if (currentlySelectedLiIndex > 0) {
    		// Move the selected li up one (note "before" is actually a move)
    		$("#sendElementList li:eq(" + (currentlySelectedLiIndex-1) + ")").before(currentlySelectedLi);
    	}
    } );

    // This adds the click-event handler for the Send Elements Down button
    $("#sendElementListDown").click( function() {
		var currentlySelectedLi = $("#sendElementList li.selected");
		var currentlySelectedLiIndex = $(currentlySelectedLi).index();
		
    	if (currentlySelectedLiIndex < ($('#sendElementList li').length-1) ) {
    		// Move the selected li down one (note "before" is actually a move)
    		$("#sendElementList li:eq(" + (currentlySelectedLiIndex+1) + ")").after(currentlySelectedLi);
    	}
    } );

    // This adds the click-event handler for the Send Elements Remove button
    $("#sendElementListRemove").click( function() {
		var currentlySelectedLi = $("#sendElementList li.selected");
		var currentlySelectedLiText = $(currentlySelectedLi).text();
   		$(currentlySelectedLi).remove();

   		$("#inputNewSendElement").val(currentlySelectedLiText);

   		validateRequiredList(sendElementList);
    } );

}

// Add listener to an li of the Audit Elements list
function addListenerToNewAuditElement(newLi) {
	// This adds the click-event handler for the Audit Elements items
    $(newLi).click( function() {
    	$("div#auditElementListButtons input[type=button]").attr("disabled", false);
    	
    	$("#auditElementList li").removeClass('selected');
        $(this).addClass('selected');
    } );

}

// Add listeners to the Audit Elements buttons
function addListenersForAuditElements() {
	var auditElementList = $("#auditElementList"); 
	
    // This adds the click-event handler for the Audit Elements Add button
    $("#auditElementListAdd").click( function() {
    	var newAuditElement = $("#inputNewAuditElement").val();
    	if (newAuditElement.length > 0) {
       		$("#auditElementList").append("<li>" + newAuditElement + "</li>");
       		addListenerToNewAuditElement($("#auditElementList li:last"));
       		$("#inputNewAuditElement").val("");
    	}

   		validateRequiredList(auditElementList);
    } );

    // This adds the click-event handler for the Audit Elements Up button
    $("#auditElementListUp").click( function() {
		var currentlySelectedLi = $("#auditElementList li.selected");
		var currentlySelectedLiIndex = $(currentlySelectedLi).index();
    	if (currentlySelectedLiIndex > 0) {
    		// Move the selected li up one (note "before" is actually a move)
    		$("#auditElementList li:eq(" + (currentlySelectedLiIndex-1) + ")").before(currentlySelectedLi);
    	}
    } );

    // This adds the click-event handler for the Audit Elements Down button
    $("#auditElementListDown").click( function() {
		var currentlySelectedLi = $("#auditElementList li.selected");
		var currentlySelectedLiIndex = $(currentlySelectedLi).index();
		
    	if (currentlySelectedLiIndex < ($('#auditElementList li').length-1) ) {
    		// Move the selected li down one (note "before" is actually a move)
    		$("#auditElementList li:eq(" + (currentlySelectedLiIndex+1) + ")").after(currentlySelectedLi);
    	}
    } );

    // This adds the click-event handler for the Audit Elements Remove button
    $("#auditElementListRemove").click( function() {
		var currentlySelectedLi = $("#auditElementList li.selected");
		var currentlySelectedLiText = $(currentlySelectedLi).text();
   		$(currentlySelectedLi).remove();

   		$("#inputNewAuditElement").val(currentlySelectedLiText);

   		validateRequiredList(auditElementList);
    } );

}    
    