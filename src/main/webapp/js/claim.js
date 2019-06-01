/*
* Copyright 2019
* Ubiquitous Knowledge Processing (UKP) Lab
* Technische Universität Darmstadt
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

var button = "because"
var count = 0;
var filler = $("#filler").val();
var because_filler = $("#because_filler").val();
var but_filler = $("#but_filler").val();

var isDown = false;
$("span").mousedown(function() {
        isDown = true;      // When mouse goes down, set isDown to true
    })
    .mouseup(function() {
        isDown = false;    // When mouse goes up, set isDown to false
    });


function highlighting(){
    console.log("start highlighting function");
    var accepted = checkIfaccepted();
    if (!accepted){
        alert("accept the HIT!");

        return false;
    }
    var selected = $("input[type='radio'][name='intext']:checked");
    if (selected.length > 0) {
        selectedVal = selected.val();
        if (selectedVal.indexOf("false") !== -1) {
            return false;
        }
    }

    $(".highlighted_element").unwrap().removeClass("highlighted_element").removeClass("element_"+count).removeClass(""+count).removeClass('noselect');
        var selection = window.getSelection();

        if (selection.toString() != "") {   // if the user selected something
            var range;
            if (!IE) {
                range = selection.getRangeAt(0);
            }
            else if (IE) {
                range = document.selection.createRange();
            }
            var allWithinRangeParent = range.commonAncestorContainer.getElementsByTagName("span","br");
            if (typeof allWithinRangeParent == 'undefined'){
                return false;
            }
            var allSelected = [];

            for (var i = 0, el; el = allWithinRangeParent[i]; i++) {
                if (selection.containsNode(el, true)) {
                    allSelected.push(el);
                    var className = el.className;
                    $(el).addClass("highlighted_element");
                }
            }
			if(allSelected.length == 0){
				return;
			}
            var isStart = true;
            var isEnd = false;

            var firstSelectedElement = allSelected[0];

            var foundNoSelect = false;
            var foundSelectedAfterNoSelect = false;
            $(firstSelectedElement).nextAll().each( function () {
                var sibling=this.className;

                if (sibling.indexOf("noselect") > -1) {
                    foundNoSelect = true;
                }
                if (foundNoSelect) {
                    if (sibling.indexOf("highlighted_element") > -1) {
                        $(".highlighted_element").removeClass("highlighted_element");
                        selection.removeAllRanges();
                        foundSelectedAfterNoSelect = true;
                        allSelected = [];
                        return false;
                    }
                }
            });
            console.log("foundSelectedAfterNoSelect "+foundSelectedAfterNoSelect);
            for (var i = allSelected.length-1; i >= 0; i--){
                var el =  allSelected[i];
                $(el).addClass("element_"+ count);
                var className = el.className;
                console.log(className);

				//Punctuation check up here seems to be broken
                if (className.indexOf("token") > -1 && isStart) { //this part makes sure you do not highlight punctuation or space as the first token
                    isStart = false;
                }
                else if (isStart && className.indexOf("token") == -1) {
                    $(el).removeClass("highlighted_element").removeClass('noselect').removeClass("element_"+count);
                }
                else if (i == 1 && className.indexOf("token") == -1){
                    var next =  allSelected[0];
                    var nextClassName = next.className;
                    if (nextClassName.indexOf("token") == -1){
                        $(next).removeClass("highlighted_element").removeClass('noselect').removeClass("element_"+count);
                        isEnd = true;
                    }

                }

            }
			//Currently this checks for first token(s) being non-tokens (spaces or punctuations)
			//Iterates over all elements and removes them, till the first token is found
			for (var i = 0; i < allSelected.length; i++){
				var el =  allSelected[i];
                //$(el).addClass("element_"+ count);
                var className = el.className;
                console.log(className);
				
				if(className.indexOf("token") == -1){
					$(el).removeClass("highlighted_element").removeClass('noselect').removeClass("element_"+count);
				}
				else{
					break;
				}
			}
            if (!foundSelectedAfterNoSelect) {

                $('#add_because').show();
                $('#add_but').show();
            }
        }
        selection.removeAllRanges();

        if (typeof allSelected !== 'undefined') {
            console.log('All selected =', allSelected, allSelected.length);
            var text = filler;
            $(".highlighted_element").wrapAll("<span class=highlighted_"+button+" id='" + count + "' />")
            var highlighted = $(".highlighted_"+button).children();
            console.log(highlighted.length);
            for (var i = 0; i < highlighted.length; i++) {
                if (i == 0)
                    text = $(highlighted[i]).text();
                else
                    text = text + "" + $(highlighted[i]).text()

            }
            console.log($(el).html() + " text " + text);
         //   $('#remove_button').show();
            if (text !== filler) $("#button_submit").show();


        }
        else{
            text = filler;
            $(".highlighted_element").unwrap();
            $(".highlighted_element").removeClass("highlighted_element").removeClass("element_"+count);
            $(".highlighted_element").removeClass("highlighted_element").removeClass("element_"+count);
            console.log("infocus " + $("#infocus").attr("class"));
            if ($("#infocus").attr("class").indexOf("because") > -1 ){
                $("#add_because").hide();
            }
            else if ($("#infocus").attr("class").indexOf("but") > -1 ){
                $("#add_but").hide();
            }
            $("#button_submit").hide();
        }
		$("#textAnnotated"+count).html(text.toLowerCase())


}
var except1 = document.getElementById('button_submit');
var except2 = document.getElementById('add_because');
var except3 = document.getElementById('add_but');
var body = document.getElementById("wrapper");

body.addEventListener("mouseup", function () {
    highlighting();
}, false);
except1.addEventListener("mouseup", function (ev) {
	ev.stopPropagation();
}, false);
except2.addEventListener("mouseup", function (ev) {
	ev.stopPropagation();
}, false);
except3.addEventListener("mouseup", function (ev) {
	ev.stopPropagation();
}, false);
// .on("mouseover", function(){
//     if (isDown) {
//         if ($('.noselect:hover').length != 0) {
//             var selection = window.getSelection();
//             selection.removeAllRanges();
//         }
//     }
// });

var IE = false;

$(document).ready(function() {
    IE = msieversion();
    $("#textbox").attr("disabled", "disabled");
    $("#button_submit").hide();
    var el = document.getElementById('text_claim');
	$('#userAgent').val(navigator.userAgent);
    $("#buttonGuideline").click(function(){
        $("#div_guideline").toggle()
    });
    var sandbox = document.getElementById('text');
    $("body").addClass("noselect");
    $('#textbox').attr('autocomplete','off').val("").bind("cut copy paste",function(e) {
        e.preventDefault();
    });
    $('input[name="intext"][value="true"]').prop('checked', true);


    console.log("ready");
    //  highlight("text_claim");
    $('#add_because').click(function(){
        $("body").removeClass("noselect");
        if (button === "but" && $('.highlighted_element').length == 0){
            $('#add_but').show();
            var rem = count-1;
            $("#annotated_claim"+count).remove();
        }
        else {

            if (count >= 0)
                $("#remove_button").show();

            $(".highlighted_because").removeClass("highlighted_because").addClass('noselect').addClass('because_highlight');
            $(".highlighted_but").removeClass("highlighted_but").addClass('noselect').addClass('but_highlight');

            $(".highlighted_element").removeClass("highlighted_element").addClass("noselect").addClass("element_"+ count);
text
            $("#infocus").removeClass("inputbox");
        }
        count = count + 1;
        $("#infocus").attr("id",count);
        button = "because";
        document.getSelection().removeAllRanges();
        $("#divBecause").append("<div id=\"annotated_claim"+count+"\"><p class=\"claim because_box answers inputbox\" id=\"infocus"+"\"><em>"+because_filler+" </em><span id='textAnnotated"+count+"'>"+filler+"</span> " +
        "" + "<span id=\'close\' class='delete because'>x</span>" + "</p></div>");
		
		var exceptNew = document.getElementById('annotated_claim'+count);
		exceptNew.addEventListener("mouseup", function (ev) {
			ev.stopPropagation();
		}, false);
        $('#add_because').hide();
        $('#add_but').show();

        $('input[name="intext"][value="true"]').prop('checked', true);

    });

    $(document).on('click','.delete',function(){
        $('input[name="intext"][value="true"]').prop('checked', true);
        $(".because_box").css("box-shadow", "0 0 5px #25ff00").css("color", "black");
        $(".but_box").css("box-shadow", "0 0 5px #ff3651").css("color", "black");
        $("#textbox").attr("disabled", "disabled").removeClass("inputbox");
        var annotatedId = $(this).parent().parent().prop("id");
        var res = annotatedId.replace("annotated_claim", "");
        console.log(".element_"+res);
        $(".element_"+res).unwrap().removeClass("highlighted_element").removeClass("element_"+res).removeClass('noselect');
        $("#"+annotatedId).remove();
        $("#add_because").show();
        $("#add_but").show();
        $("#button_submit").show();

        if ($(".delete").length < 1) {
            $("#button_submit").hide();

        }
        $("body").addClass("noselect");

    });



    $('#add_but').click(function(){
        $("body").removeClass("noselect");
        if (button === "because" && $('.highlighted_element').length == 0){
            $('#add_because').show();
            var rem = count-1;
            $("#annotated_claim"+count).remove();
        }
        else {
            if (count <= 0)
                $("#remove_button").hide();
            $(".highlighted_because").removeClass("highlighted_because").addClass('noselect').addClass('because_highlight');
            $(".highlighted_but").removeClass("highlighted_but").addClass('noselect').addClass('but_highlight');;
            $(".highlighted_element").removeClass("highlighted_element").addClass("noselect").addClass("element_"+ count );
            $("#infocus").removeClass("inputbox");
            $('#remove_button').show();
        }
        count = count +1;

        button = "but";
        document.getSelection().removeAllRanges();
        $("#infocus").attr("id",count);
        $("#divBut").append("<div id=\"annotated_claim"+count+"\"><p class=\"claim but_box answers inputbox\" id=\"infocus"+"\"><em>"+but_filler+" </em></em><span id='textAnnotated"+count+"'>"+filler+"</span> " +
            "" + "<span id=\'close\' class='delete but'>x</span>" + "</p></div>");
        $('#add_but').hide()

    });





});


$("input[type=radio][name='intext']").change(function() {
    var selectedVal = "";
    var selected = $("input[type='radio'][name='intext']:checked");
    if (selected.length > 0) {
        selectedVal = selected.val();
        if (selectedVal.indexOf("true") !== -1){
            $("#button_submit").hide();
            if ($('.highlighted_because').length > 0 || $(".highlighted_but").length > 0) $("#button_submit").show();
            $("#textbox").attr("disabled", "disabled").removeClass("inputbox");
            $("#infocus").addClass("inputbox");
			$("#textbox").val("");
            if ($(".delete").length > 0) {
                $("body").removeClass("noselect");
            }
            $('#add_but').show();
            $('#add_because').show();
            $(".because_box").css("box-shadow", "0 0 5px #25ff00").css("color", "black");
            $(".but_box").css("box-shadow", "0 0 5px #ff3651").css("color", "black");
        }
        else {
            $("#button_submit").hide();
            $("#textbox").removeAttr("disabled").addClass("inputbox");
            var selection = window.getSelection();
            selection.removeAllRanges();
            $('body').addClass('noselect');
            $('#add_but').hide();
            $('#add_because').hide();
            $(".because_box").css("box-shadow", "0 0 5px grey").css("color", "grey");
            $(".but_box").css("box-shadow", "0 0 5px grey").css("color", "grey");
            if ( $("#textbox").val().length > 15 )
            {
                $("#button_submit").show();
            }
			
			
			for(var i = count; i > 0; i--){
				$(".element_"+i).unwrap();
				$(".element_"+i).removeClass("noselect");
				$(".element_"+i).removeClass("element_"+i);
				$("#annotated_claim"+i).remove();
			}
        }
		
    }

});

$("#button_submit").click(function(){
    var result = collectResults();
    console.log("result: "+result+""+$("#textbox").val());
    $("#textinput").val($("#textbox").val());
    $("#tokens").val(result);
});

function collectResults(){
    $(".highlighted_because").addClass("because_highlight");
    $(".highlighted_but").addClass("but_highlight");

    var because = $(".because_highlight");
    var str = "";
    for (var i = 0; i < because.length; i++) {
        el = because[i];
        var becauseid = $(el).attr("id");
        var children = el.childNodes;
        str = str + "[becauseid:" + becauseid+"{";
        for (var k = 0; k < children.length; k++) {
            var childid = $(children[k]).attr("id");
            str = str+""+childid+","
        }
        str = str+"}],";

    }
    var but = $(".but_highlight");
	
    for (var i = 0; i < but.length; i++) {
        el = but[i];
        var butid = $(el).attr("id");
        var children = el.childNodes;
        str = str + "[butid:" + butid+"{";
        for (var k = 0; k < children.length; k++) {
            var childid = $(children[k]).attr("id");
            str = str+""+childid+","
        }
        str = str+"}],";
    }
	str = str.replace(/.$/,"")
    return str;
}


$('body').keyup(function(e){
    if(e.keyCode == 46 || e.keyCode==8) {
        if ( $("#textbox").val().length < 15 ){
            $("#button_submit").hide();
        }
    }
});

$( "body" ).keypress(function() {
    if ( $("#textbox").val().length > 15 )
    {
        $("#button_submit").show();
    }
    else {
        $("#button_submit").hide();
    }
});

function expandtoword(range)
{
    if (range.collapsed)
    {
        return;
    }

    while (range.startOffset > 0 && range.toString()[0].match(/\w/))
    {
        range.setStart(range.startContainer, range.startOffset - 1);
    }

    while (range.endOffset < range.endContainer.length && range.toString()[range.toString().length - 1].match(/\w/))
    {
        range.setEnd(range.endContainer, range.endOffset + 1);
    }
}


function gup( name )
{
    var regexS = "[\\?&amp;]"+name+"=([^&amp;#]*)";
    var regex = new RegExp( regexS );
    var tmpURL = window.location.href;
    var results = regex.exec( tmpURL );
    if( results == null )
        return "";
    else
        return results[1];
}

function decode(strToDecode)
{
    var encoded = strToDecode;
    return unescape(encoded.replace(/\+/g,  " "));
}

function checkIfaccepted()
{
    msieversion();
    document.getElementById('assignmentId').value = gup('assignmentId');


    //
    // Check if the worker is PREVIEWING the HIT or if they've ACCEPTED the HIT
    //
    console.log("value is " + " "+ document.getElementById('assignmentId').value)

    if (gup('assignmentId') == "ASSIGNMENT_ID_NOT_AVAILABLE")
    {
        // If we're previewing, disable the button and give it a helpful message

        console.log(gup('assignmentId'));

        var inputs = document.getElementsByTagName("INPUT");
        for (var i = 0; i < inputs.length; i++) {
            console.log(inputs[i]);
            inputs[i].disabled = true;
        }


        document.getElementById('button_submit').disabled = true;
        document.getElementById('button_submit').value = "ACCEPT THE HIT";
        return false;
    } else {
        var form = document.getElementById('mturk_form');
        if (document.referrer && ( document.referrer.indexOf('workersandbox') != -1) ) {
            form.action = "https://workersandbox.mturk.com/mturk/externalSubmit";
        }
        return true;
    }
}


function msieversion()
{

    if(navigator.userAgent.indexOf('MSIE')!==-1
        || navigator.appVersion.indexOf('Trident/') > 0){
        alert("Internet Explorer is not supported. Please use another browser. \n" +
            "Internet Explorer wird nicht unterstützt. Bitte benutzen Sie einen anderen Browser. ");
        return true;
    }

    else  // If another browser, return 0
    {
        console.log("not IE "+ navigator.userAgent);
        return false;
    }

}
