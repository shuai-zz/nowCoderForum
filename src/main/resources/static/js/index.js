$(function () {
    $("#publishBtn").click(publish);
});

function publish() {
    $("#publishModal").modal("hide");

    // 应该在项目中所有ajax发送请求之前都加入这段逻辑，为了省事，直接禁用掉了Spring Security中防止CSRF攻击的措施。

    // 发送 AJAX 请求之前，将CSRF令牌设置到请求的消息头中
    // var token = $("meta[name='_csrf']").attr("content");
    // var header = $("meta[name='_csrf_header']").attr("content");
    // $(document).ajaxSend(function (e, xhr, options) {
    //     xhr.setRequestHeader(header, token);
    // });

    // get title and content
    var title = $("#recipient-name").val();
    var content = $("#message-text").val();
    // AJAX(POST)
    $.post(
        CONTEXT_PATH + "/discuss/add",
        {"title": title, "content": content},
        function (data) {
            data = $.parseJSON(data);
            // display hint message
            $("#hintBody").text(data.msg);
            // show hint message
            $("#hintModal").modal("show");
            // hide hint message
            setTimeout(function () {
                $("#hintModal").modal("hide");
                // refresh page
                if (data.code === 0) {
                    window.location.reload();
                }
            }, 2000);
        }
    );
}